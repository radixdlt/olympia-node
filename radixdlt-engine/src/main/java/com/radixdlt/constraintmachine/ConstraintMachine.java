/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 */

package com.radixdlt.constraintmachine;

import com.google.common.collect.ImmutableList;
import com.google.common.hash.HashCode;
import com.google.common.reflect.TypeToken;

import com.radixdlt.atom.Atom;
import com.radixdlt.atom.Substate;
import com.radixdlt.atom.SubstateId;
import com.radixdlt.atom.SubstateSerializer;
import com.radixdlt.atomos.Result;
import com.radixdlt.constraintmachine.WitnessValidator.WitnessValidatorResult;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.crypto.ECDSASignature;
import com.radixdlt.serialization.DeserializeException;
import com.radixdlt.store.CMStore;
import com.radixdlt.utils.Ints;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * An implementation of a UTXO based constraint machine which uses Radix's atom structure.
 */
// FIXME: unchecked, rawtypes
@SuppressWarnings({"unchecked", "rawtypes"})
public final class ConstraintMachine {
	private static final int DATA_MAX_SIZE = 256;
	private static final int MAX_NUM_MESSAGES = 1;

	private static final boolean[] truefalse = new boolean[] {
		true, false
	};

	public static class Builder {
		private Predicate<Particle> virtualStoreLayer;
		private Function<Particle, Result> particleStaticCheck;
		private Function<TransitionToken, TransitionProcedure<Particle, UsedData, Particle, UsedData>> particleProcedures;

		public Builder setParticleStaticCheck(Function<Particle, Result> particleStaticCheck) {
			this.particleStaticCheck = particleStaticCheck;
			return this;
		}

		public Builder setParticleTransitionProcedures(
			Function<TransitionToken, TransitionProcedure<Particle, UsedData, Particle, UsedData>> particleProcedures
		) {
			this.particleProcedures = particleProcedures;
			return this;
		}

		public Builder setVirtualStoreLayer(Predicate<Particle> virtualStoreLayer) {
			this.virtualStoreLayer = virtualStoreLayer;
			return this;
		}

		public ConstraintMachine build() {
			return new ConstraintMachine(
				virtualStoreLayer,
				particleStaticCheck,
				particleProcedures
			);
		}
	}

	private final Predicate<Particle> virtualStoreLayer;
	private final Function<Particle, Result> particleStaticCheck;
	private final Function<TransitionToken, TransitionProcedure<Particle, UsedData, Particle, UsedData>> particleProcedures;

	ConstraintMachine(
		Predicate<Particle> virtualStoreLayer,
		Function<Particle, Result> particleStaticCheck,
		Function<TransitionToken, TransitionProcedure<Particle, UsedData, Particle, UsedData>> particleProcedures
	) {
		this.virtualStoreLayer = virtualStoreLayer;
		this.particleStaticCheck = particleStaticCheck;
		this.particleProcedures = particleProcedures;
	}

	public static final class CMValidationState implements WitnessData {
		private PermissionLevel permissionLevel;
		private TransitionToken currentTransitionToken = null;
		private Particle particleRemaining = null;
		private boolean particleRemainingIsInput;
		private UsedData particleRemainingUsed = null;
		private final Map<Integer, Particle> localUpParticles = new HashMap<>();
		private final Set<SubstateId> remoteDownParticles = new HashSet<>();
		private final HashCode witness;
		private final Optional<ECDSASignature> signature;
		private final Map<ECPublicKey, Boolean> isSignedByCache = new HashMap<>();
		private final CMStore store;
		private final CMStore.Transaction txn;
		private final Predicate<Particle> virtualStoreLayer;

		CMValidationState(
			Predicate<Particle> virtualStoreLayer,
			CMStore.Transaction txn,
			CMStore store,
			PermissionLevel permissionLevel,
			HashCode witness,
			Optional<ECDSASignature> signature
		) {
			this.virtualStoreLayer = virtualStoreLayer;
			this.txn = txn;
			this.store = store;
			this.permissionLevel = permissionLevel;
			this.witness = witness;
			this.signature = signature;
		}

		public Optional<Particle> loadUpParticle(SubstateId substateId) {
			if (remoteDownParticles.contains(substateId)) {
				return Optional.empty();
			}

			return store.loadUpParticle(txn, substateId);
		}

		public void setCurrentTransitionToken(TransitionToken currentTransitionToken) {
			this.currentTransitionToken = currentTransitionToken;
		}

		public void bootUp(int instructionIndex, Substate substate) {
			localUpParticles.put(instructionIndex, substate.getParticle());
		}

		public Optional<CMErrorCode> virtualShutdown(Substate substate) {
			if (remoteDownParticles.contains(substate.getId())) {
				return Optional.of(CMErrorCode.SPIN_CONFLICT);
			}

			if (!virtualStoreLayer.test(substate.getParticle())) {
				return Optional.of(CMErrorCode.INVALID_PARTICLE);
			}

			if (store.isVirtualDown(txn, substate.getId())) {
				return Optional.of(CMErrorCode.SPIN_CONFLICT);
			}

			remoteDownParticles.add(substate.getId());
			return Optional.empty();
		}

		public Optional<Particle> localShutdown(int index) {
			var maybeParticle = localUpParticles.remove(index);
			return Optional.ofNullable(maybeParticle);
		}

		public Optional<Particle> shutdown(SubstateId substateId) {
			var maybeParticle = loadUpParticle(substateId);
			remoteDownParticles.add(substateId);
			return maybeParticle;
		}

		@Override
		public boolean isSignedBy(ECPublicKey publicKey) {
			return this.isSignedByCache.computeIfAbsent(publicKey, this::verifySignedWith);
		}

		private boolean verifySignedWith(ECPublicKey publicKey) {
			if (signature.isEmpty() || witness == null) {
				return false;
			}

			return publicKey.verify(witness, signature.get());
		}

		Particle getCurParticle() {
			return particleRemaining;
		}

		boolean spinClashes(boolean nextIsInput) {
			return particleRemaining != null && nextIsInput == particleRemainingIsInput;
		}

		TypeToken<? extends UsedData> getInputUsedType() {
			return particleRemaining != null && particleRemainingIsInput && particleRemainingUsed != null
				? particleRemainingUsed.getTypeToken() : TypeToken.of(VoidUsedData.class);
		}

		TypeToken<? extends UsedData> getOutputUsedType() {
			return particleRemaining != null && !particleRemainingIsInput && particleRemainingUsed != null
				? particleRemainingUsed.getTypeToken() : TypeToken.of(VoidUsedData.class);
		}

		UsedData getInputUsed() {
			return particleRemaining != null && particleRemainingIsInput ? particleRemainingUsed : null;
		}

		UsedData getOutputUsed() {
			return particleRemaining != null && !particleRemainingIsInput ? particleRemainingUsed : null;
		}

		void pop() {
			this.particleRemaining = null;
			this.particleRemainingUsed = null;
		}

		void popAndReplace(Particle particle, boolean isInput, UsedData particleRemainingUsed) {
			this.particleRemaining = particle;
			this.particleRemainingIsInput = isInput;
			this.particleRemainingUsed = particleRemainingUsed;
		}

		void updateUsed(UsedData particleRemainingUsed) {
			this.particleRemainingUsed = particleRemainingUsed;
		}

		boolean isEmpty() {
			return this.particleRemaining == null;
		}

		@Override
		public String toString() {
			StringBuilder builder = new StringBuilder();
			builder.append("CMTrace:\n[\n");
			if (particleRemaining != null) {
				builder
					.append("  Remaining (")
					.append(this.particleRemainingIsInput ? "input" : "output")
					.append("): ")
					.append(this.particleRemaining)
					.append("\n  Used: ")
					.append(this.particleRemainingUsed);
			} else {
				builder.append("  Remaining: [empty]");
			}
			builder.append("\n  TransitionToken: ").append(currentTransitionToken);
			builder.append("\n]");

			return builder.toString();
		}
	}

	/**
	 * Executes a transition procedure given the next spun particle and a current validation state.
	 *
	 * @param dp pointer of the next spun particle
	 * @param validationState local state of validation
	 * @return the first error found, otherwise an empty optional
	 */
	Optional<CMError> validateParticle(CMValidationState validationState, Particle nextParticle, boolean isInput, DataPointer dp) {
		final Particle curParticle = validationState.getCurParticle();

		if (validationState.spinClashes(isInput)) {
			return Optional.of(
				new CMError(
					dp,
					CMErrorCode.PARTICLE_REGISTER_SPIN_CLASH,
					validationState,
					validationState.toString()
				)
			);
		}

		final Particle inputParticle = isInput ? nextParticle : curParticle;
		final Particle outputParticle = isInput ? curParticle : nextParticle;
		final TransitionToken transitionToken = new TransitionToken(
			inputParticle != null ? inputParticle.getClass() : VoidParticle.class,
			validationState.getInputUsedType(),
			outputParticle != null ? outputParticle.getClass() : VoidParticle.class,
			validationState.getOutputUsedType()
		);

		validationState.setCurrentTransitionToken(transitionToken);

		final var transitionProcedure = this.particleProcedures.apply(transitionToken);

		if (transitionProcedure == null) {
			if (inputParticle == null || outputParticle == null) {
				validationState.popAndReplace(nextParticle, isInput, null);
				return Optional.empty();
			}

			return Optional.of(
				new CMError(
					dp,
					CMErrorCode.MISSING_TRANSITION_PROCEDURE,
					validationState,
					"TransitionToken{" + transitionToken + "}"
				)
			);
		}

		final PermissionLevel requiredPermissionLevel = transitionProcedure.requiredPermissionLevel();
		if (validationState.permissionLevel.compareTo(requiredPermissionLevel) < 0) {
			return Optional.of(
				new CMError(
					dp,
					CMErrorCode.INVALID_EXECUTION_PERMISSION,
					validationState
				)
			);
		}

		final UsedData inputUsed = validationState.getInputUsed();
		final UsedData outputUsed = validationState.getOutputUsed();

		// Precondition check
		final Result preconditionCheckResult = transitionProcedure.precondition(
			inputParticle,
			inputUsed,
			outputParticle,
			outputUsed
		);
		if (preconditionCheckResult.isError()) {
			return Optional.of(
				new CMError(
					dp,
					CMErrorCode.TRANSITION_PRECONDITION_FAILURE,
					validationState,
					preconditionCheckResult.getErrorMessage()
				)
			);
		}

		Optional<UsedData> prevUsedData = null;
		for (boolean testInput : truefalse) {

			UsedCompute<Particle, UsedData, Particle, UsedData> usedCompute
				= testInput ? transitionProcedure.inputUsedCompute() : transitionProcedure.outputUsedCompute();

			try {
				final Optional<UsedData> usedData = usedCompute.compute(inputParticle, inputUsed, outputParticle, outputUsed);
				if (usedData.isPresent()) {
					if (prevUsedData != null && prevUsedData.isPresent()) {
						return Optional.of(
							new CMError(
								dp,
								CMErrorCode.NO_FULL_POP_ERROR,
								validationState
							)
						);
					}

					if (isInput == testInput) {
						validationState.popAndReplace(nextParticle, isInput, usedData.get());
					} else {
						validationState.updateUsed(usedData.get());
					}
				} else {
					final WitnessValidator<Particle> witnessValidator = testInput ? transitionProcedure.inputWitnessValidator()
						: transitionProcedure.outputWitnessValidator();
					final WitnessValidatorResult inputWitness = witnessValidator.validate(
						testInput ? inputParticle : outputParticle, validationState
					);

					if (inputWitness.isError()) {
						return Optional.of(
							new CMError(
								dp,
								CMErrorCode.WITNESS_ERROR,
								validationState,
								inputWitness.getErrorMessage()
							)
						);
					}

					if (prevUsedData != null && !prevUsedData.isPresent()) {
						validationState.pop();
					}
				}
				prevUsedData = usedData;
			} catch (ArithmeticException e) {
				return Optional.of(new CMError(dp, CMErrorCode.ARITHMETIC_ERROR, validationState, e.getMessage()));
			}
		}

		validationState.setCurrentTransitionToken(null);

		return Optional.empty();
	}

	public static List<REInstruction> toInstructions(List<byte[]> bytesList) {
		Objects.requireNonNull(bytesList);
		ImmutableList.Builder<REInstruction> instructionsBuilder = ImmutableList.builder();

		Iterator<byte[]> bytesIterator = bytesList.iterator();
		while (bytesIterator.hasNext()) {
			byte[] bytes = bytesIterator.next();
			byte[] dataBytes = bytesIterator.next();
			var instruction = REInstruction.create(bytes[0], dataBytes);
			instructionsBuilder.add(instruction);
		}

		return instructionsBuilder.build();
	}

	/**
	 * Executes transition procedures and witness validators in a particle group and validates
	 * that the particle group is well formed.
	 *
	 * @return the first error found, otherwise an empty optional
	 */
	Optional<CMError> validateInstructions(
		CMValidationState validationState,
		Atom atom,
		List<ParsedInstruction> parsedInstructions
	) {
		var rawInstructions = toInstructions(atom.getInstructions());

		long particleGroupIndex = 0;
		long particleIndex = 0;
		int instructionIndex = 0;
		int numMessages = 0;

		for (var inst : rawInstructions) {
			final DataPointer dp = DataPointer.ofParticle(particleGroupIndex, particleIndex);
			if (inst.getData().length > DATA_MAX_SIZE)	 {
				return Optional.of(new CMError(
					dp, CMErrorCode.DATA_TOO_LARGE, validationState, "Length is " + inst.getData().length
				));
			}

			if (inst.getMicroOp() == com.radixdlt.constraintmachine.REInstruction.REOp.UP) {
				// TODO: Cleanup indexing of substate class
				final Particle nextParticle;
				try {
					nextParticle = SubstateSerializer.deserialize(inst.getData());
				} catch (DeserializeException e) {
					return Optional.of(new CMError(dp, CMErrorCode.INVALID_PARTICLE, validationState));
				}

				final Result staticCheckResult = particleStaticCheck.apply(nextParticle);
				if (staticCheckResult.isError()) {
					var errMsg = staticCheckResult.getErrorMessage();
					return Optional.of(new CMError(dp, CMErrorCode.INVALID_PARTICLE, validationState, errMsg));
				}
				var substate = Substate.create(nextParticle, SubstateId.ofSubstate(atom, instructionIndex));
				validationState.bootUp(instructionIndex, substate);
				Optional<CMError> error = validateParticle(validationState, nextParticle, false, dp);
				if (error.isPresent()) {
					return error;
				}

				parsedInstructions.add(ParsedInstruction.of(inst, substate, Spin.UP));
				particleIndex++;
			} else if (inst.getMicroOp() == com.radixdlt.constraintmachine.REInstruction.REOp.VDOWN) {
				final Particle nextParticle;
				try {
					nextParticle = SubstateSerializer.deserialize(inst.getData());
				} catch (DeserializeException e) {
					return Optional.of(new CMError(dp, CMErrorCode.INVALID_PARTICLE, validationState));
				}
				final Result staticCheckResult = particleStaticCheck.apply(nextParticle);
				if (staticCheckResult.isError()) {
					var errMsg = staticCheckResult.getErrorMessage();
					return Optional.of(new CMError(dp, CMErrorCode.INVALID_PARTICLE, validationState, errMsg));
				}

				var substate = Substate.create(nextParticle, SubstateId.ofVirtualSubstate(inst.getData()));
				var stateError = validationState.virtualShutdown(substate);
				if (stateError.isPresent()) {
					return Optional.of(new CMError(dp, stateError.get(), validationState));
				}

				Optional<CMError> error = validateParticle(validationState, nextParticle, true, dp);
				if (error.isPresent()) {
					return error;
				}

				parsedInstructions.add(ParsedInstruction.of(inst, substate, Spin.DOWN));
				particleIndex++;
			} else if (inst.getMicroOp() == com.radixdlt.constraintmachine.REInstruction.REOp.DOWN) {
				var substateId = SubstateId.fromBytes(inst.getData());
				var maybeParticle = validationState.shutdown(substateId);
				if (maybeParticle.isEmpty()) {
					return Optional.of(new CMError(dp, CMErrorCode.SPIN_CONFLICT, validationState));
				}

				var particle = maybeParticle.get();
				Optional<CMError> error = validateParticle(validationState, particle, true, dp);
				if (error.isPresent()) {
					return error;
				}

				var substate = Substate.create(particle, substateId);
				parsedInstructions.add(ParsedInstruction.of(inst, substate, Spin.DOWN));
				particleIndex++;
			} else if (inst.getMicroOp() == REInstruction.REOp.LDOWN) {
				int index = Ints.fromByteArray(inst.getData());
				var maybeParticle = validationState.localShutdown(index);
				if (maybeParticle.isEmpty()) {
					return Optional.of(new CMError(dp, CMErrorCode.LOCAL_NONEXISTENT, validationState));
				}

				var particle = maybeParticle.get();
				Optional<CMError> error = validateParticle(validationState, particle, true, dp);
				if (error.isPresent()) {
					return error;
				}

				var substateId = SubstateId.ofSubstate(atom, index);
				var substate = Substate.create(particle, substateId);
				parsedInstructions.add(ParsedInstruction.of(inst, substate, Spin.DOWN));
				particleIndex++;
			} else if (inst.getMicroOp() == REInstruction.REOp.MSG) {
				numMessages++;
				if (numMessages > MAX_NUM_MESSAGES) {
					return Optional.of(
						new CMError(dp, CMErrorCode.TOO_MANY_MESSAGES, validationState)
					);
				}
			} else if (inst.getMicroOp() == com.radixdlt.constraintmachine.REInstruction.REOp.END) {
				if (particleIndex == 0) {
					return Optional.of(
						new CMError(dp, CMErrorCode.EMPTY_PARTICLE_GROUP, validationState)
					);
				}

				if (!validationState.isEmpty()) {
					return Optional.of(new CMError(dp, CMErrorCode.UNEQUAL_INPUT_OUTPUT, validationState));
				}
				particleGroupIndex++;
				particleIndex = 0;
			} else {
				throw new IllegalStateException("Unknown CM Operation: " + inst.getMicroOp());
			}

			instructionIndex++;
		}

		if (particleIndex != 0) {
			return Optional.of(new CMError(
				DataPointer.ofParticle(particleGroupIndex, particleIndex),
				CMErrorCode.MISSING_PARTICLE_GROUP,
				validationState
			));
		}

		return Optional.empty();
	}

	/**
	 * Validates a CM instruction and calculates the necessary state checks and post-validation
	 * write logic.
	 *
	 * @return the first error found, otherwise an empty optional
	 */
	public Optional<CMError> validate(
		CMStore.Transaction txn,
		CMStore cmStore,
		Atom atom,
		PermissionLevel permissionLevel,
		List<ParsedInstruction> parsedInstructions
	) {
		final CMValidationState validationState = new CMValidationState(
			virtualStoreLayer,
			txn,
			cmStore,
			permissionLevel,
			atom.computeHashToSign(),
			atom.getSignature()
		);

		return this.validateInstructions(validationState, atom, parsedInstructions);
	}
}
