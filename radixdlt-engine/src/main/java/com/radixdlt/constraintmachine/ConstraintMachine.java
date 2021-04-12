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
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.crypto.ECDSASignature;
import com.radixdlt.serialization.DeserializeException;
import com.radixdlt.store.CMStore;
import com.radixdlt.utils.Ints;
import com.radixdlt.utils.Pair;

import java.util.ArrayList;
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
	private static final int DATA_MAX_SIZE = 255;
	private static final int MAX_NUM_MESSAGES = 1;

	private static final boolean[] truefalse = new boolean[] {
		true, false
	};

	public static class Builder {
		private Predicate<Particle> virtualStoreLayer;
		private Function<Particle, Result> particleStaticCheck;
		private Function<TransitionToken, TransitionProcedure<Particle, Particle, ReducerState>> particleProcedures;

		public Builder setParticleStaticCheck(Function<Particle, Result> particleStaticCheck) {
			this.particleStaticCheck = particleStaticCheck;
			return this;
		}

		public Builder setParticleTransitionProcedures(
			Function<TransitionToken, TransitionProcedure<Particle, Particle, ReducerState>> particleProcedures
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
	private final Function<TransitionToken, TransitionProcedure<Particle, Particle, ReducerState>> particleProcedures;

	ConstraintMachine(
		Predicate<Particle> virtualStoreLayer,
		Function<Particle, Result> particleStaticCheck,
		Function<TransitionToken, TransitionProcedure<Particle, Particle, ReducerState>> particleProcedures
	) {
		this.virtualStoreLayer = virtualStoreLayer;
		this.particleStaticCheck = particleStaticCheck;
		this.particleProcedures = particleProcedures;
	}

	public static final class CMValidationState {
		private PermissionLevel permissionLevel;
		private TransitionToken currentTransitionToken = null;

		private Particle particleRemaining = null;
		private boolean particleRemainingIsInput;
		private ReducerState particleRemainingUsed = null;

		private final Map<Integer, Particle> localUpParticles = new HashMap<>();
		private final Set<SubstateId> remoteDownParticles = new HashSet<>();
		private final HashCode witness;
		private final Optional<ECDSASignature> signature;
		private final CMStore store;
		private final CMStore.Transaction txn;
		private final Predicate<Particle> virtualStoreLayer;
		private ECPublicKey signatureRequired;

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

		public Optional<Particle> localRead(int index) {
			var maybeParticle = localUpParticles.get(index);
			return Optional.ofNullable(maybeParticle);
		}

		public Optional<Particle> read(SubstateId substateId) {
			return loadUpParticle(substateId);
		}

		public Optional<Particle> shutdown(SubstateId substateId) {
			var maybeParticle = loadUpParticle(substateId);
			remoteDownParticles.add(substateId);
			return maybeParticle;
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

		TypeToken<? extends ReducerState> getUsedType() {
			return particleRemaining != null && particleRemainingUsed != null
				? particleRemainingUsed.getTypeToken() : TypeToken.of(VoidReducerState.class);
		}

		ReducerState getUsed() {
			return particleRemaining != null ? particleRemainingUsed : null;
		}

		void pop() {
			this.particleRemaining = null;
			this.particleRemainingUsed = null;
		}

		void popAndReplace(Particle particle, boolean isInput, ReducerState particleRemainingUsed) {
			this.particleRemaining = particle;
			this.particleRemainingIsInput = isInput;
			this.particleRemainingUsed = particleRemainingUsed;
		}

		void updateState(ReducerState particleRemainingUsed) {
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
	 * @param validationState local state of validation
	 * @return the first error found, otherwise an empty optional
	 */
	Optional<Pair<CMErrorCode, String>> validateParticle(
		CMValidationState validationState,
		Particle nextParticle,
		boolean isInput,
		boolean isRead
	) {
		final Particle curParticle = validationState.getCurParticle();

		if (validationState.spinClashes(isInput)) {
			return Optional.of(Pair.of(CMErrorCode.PARTICLE_REGISTER_SPIN_CLASH, null));
		}

		if (isRead && validationState.getUsed() != null) {
			return Optional.of(Pair.of(CMErrorCode.PARTICLE_REGISTER_SPIN_CLASH, "Read clash"));
		}

		final Particle inputParticle = isInput ? nextParticle : curParticle;
		final Particle outputParticle = isInput ? curParticle : nextParticle;
		final TransitionToken transitionToken = new TransitionToken(
			inputParticle != null ? inputParticle.getClass() : VoidParticle.class,
			outputParticle != null ? outputParticle.getClass() : VoidParticle.class,
			isRead ? TypeToken.of(ReadOnlyData.class) : validationState.getUsedType()
		);

		validationState.setCurrentTransitionToken(transitionToken);

		final var transitionProcedure = this.particleProcedures.apply(transitionToken);
		if (inputParticle == null || outputParticle == null) {
			validationState.popAndReplace(nextParticle, isInput, isRead ? new ReadOnlyData() : null);
			return Optional.empty();
		}

		if (transitionProcedure == null) {
			return Optional.of(Pair.of(CMErrorCode.MISSING_TRANSITION_PROCEDURE, "TransitionToken{" + transitionToken + "}"));
		}

		final PermissionLevel requiredPermissionLevel = transitionProcedure.requiredPermissionLevel();
		if (validationState.permissionLevel.compareTo(requiredPermissionLevel) < 0) {
			return Optional.of(Pair.of(CMErrorCode.INVALID_EXECUTION_PERMISSION, null));
		}

		final var used = validationState.getUsed();

		// Precondition check
		final Result preconditionCheckResult = transitionProcedure.precondition(
			inputParticle,
			outputParticle,
			used
		);
		if (preconditionCheckResult.isError()) {
			return Optional.of(Pair.of(CMErrorCode.TRANSITION_PRECONDITION_FAILURE, preconditionCheckResult.getErrorMessage()));
		}

		var reducer = transitionProcedure.inputOutputReducer();
		var result = reducer.reduce(inputParticle, outputParticle, used);
		result.ifIncompleteElse(
			(keepInput, state) -> {
				if (isInput == keepInput) {
					validationState.popAndReplace(nextParticle, isInput, state);
				} else {
					validationState.updateState(state);
				}
			},
			validationState::pop
		);

		var pkeyMaybe = transitionProcedure.inputSignatureRequired()
			.requiredSignature(inputParticle);
		if (pkeyMaybe.isPresent()) {
			var pkey = pkeyMaybe.get();
			if (validationState.signatureRequired != null) {
				if (!validationState.signatureRequired.equals(pkey)) {
					return Optional.of(Pair.of(CMErrorCode.TOO_MANY_REQUIRED_SIGNATURES, null));
				}
			} else {
				if (!validationState.verifySignedWith(pkey)) {
					return Optional.of(Pair.of(CMErrorCode.INCORRECT_SIGNATURE, null));
				}

				validationState.signatureRequired = pkey;
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
		List<REParsedAction> parsedActions
	) {
		var parsedInstructions = new ArrayList<REParsedInstruction>();
		var rawInstructions = toInstructions(atom.getInstructions());
		long particleIndex = 0;
		int instructionIndex = 0;
		int numMessages = 0;
		var expectEnd = false;

		for (var inst : rawInstructions) {
			if (inst.getData().length > DATA_MAX_SIZE)	 {
				return Optional.of(new CMError(
					instructionIndex, CMErrorCode.DATA_TOO_LARGE, validationState, "Length is " + inst.getData().length
				));
			}

			if (expectEnd && inst.getMicroOp() != REInstruction.REOp.END) {
				return Optional.of(new CMError(instructionIndex, CMErrorCode.MISSING_PARTICLE_GROUP, validationState));
			}

			if (inst.hasSubstate()) {
				final Particle nextParticle;
				final Substate substate;

				if (inst.getMicroOp() == REInstruction.REOp.UP) {
					// TODO: Cleanup indexing of substate class
					try {
						nextParticle = SubstateSerializer.deserialize(inst.getData());
					} catch (DeserializeException e) {
						return Optional.of(new CMError(instructionIndex, CMErrorCode.INVALID_PARTICLE, validationState));
					}

					final Result staticCheckResult = particleStaticCheck.apply(nextParticle);
					if (staticCheckResult.isError()) {
						var errMsg = staticCheckResult.getErrorMessage();
						return Optional.of(new CMError(instructionIndex, CMErrorCode.INVALID_PARTICLE, validationState, errMsg));
					}
					substate = Substate.create(nextParticle, SubstateId.ofSubstate(atom, instructionIndex));
					validationState.bootUp(instructionIndex, substate);
				} else if (inst.getMicroOp() == REInstruction.REOp.VDOWN) {
					try {
						nextParticle = SubstateSerializer.deserialize(inst.getData());
					} catch (DeserializeException e) {
						return Optional.of(new CMError(instructionIndex, CMErrorCode.INVALID_PARTICLE, validationState));
					}
					final Result staticCheckResult = particleStaticCheck.apply(nextParticle);
					if (staticCheckResult.isError()) {
						var errMsg = staticCheckResult.getErrorMessage();
						return Optional.of(new CMError(instructionIndex, CMErrorCode.INVALID_PARTICLE, validationState, errMsg));
					}

					substate = Substate.create(nextParticle, SubstateId.ofVirtualSubstate(inst.getData()));
					var stateError = validationState.virtualShutdown(substate);
					if (stateError.isPresent()) {
						return Optional.of(new CMError(instructionIndex, stateError.get(), validationState));
					}
				} else if (inst.getMicroOp() == com.radixdlt.constraintmachine.REInstruction.REOp.DOWN) {
					var substateId = SubstateId.fromBytes(inst.getData());
					var maybeParticle = validationState.shutdown(substateId);
					if (maybeParticle.isEmpty()) {
						return Optional.of(new CMError(instructionIndex, CMErrorCode.SPIN_CONFLICT, validationState));
					}
					nextParticle = maybeParticle.get();
					substate = Substate.create(nextParticle, substateId);
				} else if (inst.getMicroOp() == REInstruction.REOp.LDOWN) {
					int index = Ints.fromByteArray(inst.getData());
					var maybeParticle = validationState.localShutdown(index);
					if (maybeParticle.isEmpty()) {
						return Optional.of(new CMError(instructionIndex, CMErrorCode.LOCAL_NONEXISTENT, validationState));
					}

					nextParticle = maybeParticle.get();
					var substateId = SubstateId.ofSubstate(atom, index);
					substate = Substate.create(nextParticle, substateId);
				} else if (inst.getMicroOp() == REInstruction.REOp.READ) {
					var substateId = SubstateId.fromBytes(inst.getData());
					var maybeParticle = validationState.read(substateId);
					if (maybeParticle.isEmpty()) {
						return Optional.of(new CMError(instructionIndex, CMErrorCode.READ_FAILURE, validationState));
					}

					nextParticle = maybeParticle.get();
					substate = Substate.create(nextParticle, substateId);
				} else if (inst.getMicroOp() == REInstruction.REOp.LREAD) {
					int index = Ints.fromByteArray(inst.getData());
					var maybeParticle = validationState.localRead(index);
					if (maybeParticle.isEmpty()) {
						return Optional.of(new CMError(instructionIndex, CMErrorCode.LOCAL_NONEXISTENT, validationState));
					}

					nextParticle = maybeParticle.get();
					var substateId = SubstateId.ofSubstate(atom, index);
					substate = Substate.create(nextParticle, substateId);
				} else {
					return Optional.of(new CMError(instructionIndex, CMErrorCode.UNKNOWN_OP, validationState));
				}

				var error = validateParticle(
					validationState,
					nextParticle,
					inst.getCheckSpin() == Spin.UP,
					!inst.isPush() && inst.getCheckSpin() == Spin.UP
				);
				if (error.isPresent()) {
					return Optional.of(new CMError(
						instructionIndex,
						error.get().getFirst(),
						validationState,
						error.get().getSecond()
					));
				}

				parsedInstructions.add(REParsedInstruction.of(inst, substate));
				particleIndex++;

			} else if (inst.getMicroOp() == REInstruction.REOp.MSG) {
				numMessages++;
				if (numMessages > MAX_NUM_MESSAGES) {
					return Optional.of(
						new CMError(instructionIndex, CMErrorCode.TOO_MANY_MESSAGES, validationState)
					);
				}
			} else if (inst.getMicroOp() == com.radixdlt.constraintmachine.REInstruction.REOp.END) {
				if (particleIndex == 0) {
					return Optional.of(
						new CMError(instructionIndex, CMErrorCode.EMPTY_PARTICLE_GROUP, validationState)
					);
				}

				final Pair<Particle, ReducerState> deallocated;
				if (!validationState.isEmpty()) {
					if (validationState.particleRemainingIsInput) {
						var particle = validationState.particleRemaining;
						var reducerState = validationState.particleRemainingUsed;
						var errMaybe = validateParticle(
							validationState,
							VoidParticle.create(),
							false,
							false
						);
						if (errMaybe.isPresent()) {
							return Optional.of(new CMError(
								instructionIndex,
								errMaybe.get().getFirst(),
								validationState,
								errMaybe.get().getSecond()
							));
						}
						if (validationState.isEmpty()) {
							deallocated = Pair.of(particle, reducerState);
						} else {
							return Optional.of(new CMError(instructionIndex, CMErrorCode.UNEQUAL_INPUT_OUTPUT, validationState));
						}
					} else {
						return Optional.of(new CMError(instructionIndex, CMErrorCode.UNEQUAL_INPUT_OUTPUT, validationState));
					}
				} else {
					deallocated = null;
				}

				var parsedAction = REParsedAction.create(parsedInstructions, deallocated);
				parsedActions.add(parsedAction);
				parsedInstructions = new ArrayList<>();
				particleIndex = 0;
			} else {
				throw new IllegalStateException("Unknown CM Operation: " + inst.getMicroOp());
			}

			expectEnd = validationState.isEmpty() && inst.getMicroOp() != REInstruction.REOp.END;
			instructionIndex++;
		}

		if (particleIndex != 0) {
			return Optional.of(new CMError(
				instructionIndex,
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
		List<REParsedAction> parsedActions
	) {
		final CMValidationState validationState = new CMValidationState(
			virtualStoreLayer,
			txn,
			cmStore,
			permissionLevel,
			atom.computeHashToSign(),
			atom.getSignature()
		);

		return this.validateInstructions(validationState, atom, parsedActions);
	}
}
