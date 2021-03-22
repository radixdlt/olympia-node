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

import com.google.common.hash.HashCode;
import com.google.common.reflect.TypeToken;
import com.radixdlt.DefaultSerialization;

import com.radixdlt.atomos.Result;
import com.radixdlt.crypto.HashUtils;
import com.radixdlt.identifiers.EUID;
import com.radixdlt.constraintmachine.WitnessValidator.WitnessValidatorResult;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.crypto.ECDSASignature;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.store.CMStore;
import com.radixdlt.store.SpinStateMachine;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * An implementation of a UTXO based constraint machine which uses Radix's atom structure.
 */
// FIXME: unchecked, rawtypes
@SuppressWarnings({"unchecked", "rawtypes"})
public final class ConstraintMachine {
	private static final boolean[] truefalse = new boolean[] {
		true, false
	};

	public static class Builder {
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

		public ConstraintMachine build() {
			return new ConstraintMachine(
				particleStaticCheck,
				particleProcedures
			);
		}
	}

	private final Function<Particle, Result> particleStaticCheck;
	private final Function<TransitionToken, TransitionProcedure<Particle, UsedData, Particle, UsedData>> particleProcedures;

	ConstraintMachine(
		Function<Particle, Result> particleStaticCheck,
		Function<TransitionToken, TransitionProcedure<Particle, UsedData, Particle, UsedData>> particleProcedures
	) {
		this.particleStaticCheck = particleStaticCheck;
		this.particleProcedures = particleProcedures;
	}

	public static final class CMValidationState implements WitnessData {
		private PermissionLevel permissionLevel;
		private TransitionToken currentTransitionToken = null;
		private Particle particleRemaining = null;
		private boolean particleRemainingIsInput;
		private UsedData particleRemainingUsed = null;
		private final Map<Particle, Spin> currentSpins;
		private final Map<HashCode, Particle> upParticles = new HashMap<>();
		private final HashCode witness;
		private final Map<EUID, ECDSASignature> signatures;
		private final Map<ECPublicKey, Boolean> isSignedByCache = new HashMap<>();
		private final CMStore store;
		private final CMStore.Transaction txn;

		CMValidationState(
			CMStore.Transaction txn,
			CMStore store,
			PermissionLevel permissionLevel,
			HashCode witness,
			Map<EUID, ECDSASignature> signatures
		) {
			this.txn = txn;
			this.store = store;
			this.permissionLevel = permissionLevel;
			this.currentSpins = new HashMap<>();
			this.witness = witness;
			this.signatures = signatures;
		}

		public Optional<Particle> loadUpParticle(HashCode particleHash) {
			if (upParticles.containsKey(particleHash)) {
				return Optional.of(upParticles.get(particleHash));
			}
			return store.loadUpParticle(txn, particleHash);
		}

		public void setCurrentTransitionToken(TransitionToken currentTransitionToken) {
			this.currentTransitionToken = currentTransitionToken;
		}

		public boolean checkSpinAndPush(Particle particle, Spin spin) {
			final Spin currentSpin;
			if (currentSpins.containsKey(particle)) {
				currentSpin = currentSpins.get(particle);
			} else {
				currentSpin = store.getSpin(txn, particle);
			}

			if (!currentSpin.equals(spin)) {
				return false;
			}

			final Spin nextSpin = SpinStateMachine.next(currentSpin);
			currentSpins.put(particle, nextSpin);

			var dson = DefaultSerialization.getInstance().toDson(particle, DsonOutput.Output.ALL);
			var particleHash = HashUtils.sha256(dson);
			if (nextSpin == Spin.UP) {
				upParticles.put(particleHash, particle);
			} else {
				upParticles.remove(particleHash);
			}

			return true;
		}

		public Optional<Particle> checkUpAndPush(HashCode particleHash) {
			var maybeParticle = loadUpParticle(particleHash);
			upParticles.remove(particleHash);
			return maybeParticle;
		}

		@Override
		public boolean isSignedBy(ECPublicKey publicKey) {
			return this.isSignedByCache.computeIfAbsent(publicKey, this::verifySignedWith);
		}

		private boolean verifySignedWith(ECPublicKey publicKey) {
			if (signatures == null || signatures.isEmpty() || witness == null) {
				return false;
			}

			final ECDSASignature signature = signatures.get(publicKey.euid());
			return publicKey.verify(witness, signature);
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

	/**
	 * Executes transition procedures and witness validators in a particle group and validates
	 * that the particle group is well formed.
	 *
	 * @return the first error found, otherwise an empty optional
	 */
	Optional<CMError> validateMicroInstructions(
		CMValidationState validationState,
		List<CMMicroInstruction> microInstructions,
		Map<HashCode, Particle> downedParticles
	) {
		long particleGroupIndex = 0;
		long particleIndex = 0;

		for (CMMicroInstruction cmMicroInstruction : microInstructions) {
			final DataPointer dp = DataPointer.ofParticle(particleGroupIndex, particleIndex);
			if (cmMicroInstruction.getMicroOp() == CMMicroInstruction.CMMicroOp.SPIN_UP) {
				final var nextParticle = cmMicroInstruction.getParticle();
				final Result staticCheckResult = particleStaticCheck.apply(nextParticle);
				if (staticCheckResult.isError()) {
					return Optional.of(new CMError(
						dp,
						CMErrorCode.INVALID_PARTICLE,
						validationState,
						staticCheckResult.getErrorMessage()
					));
				}
				final Spin checkSpin = cmMicroInstruction.getCheckSpin();
				boolean okay = validationState.checkSpinAndPush(nextParticle, checkSpin);
				if (!okay) {
					return Optional.of(new CMError(dp, CMErrorCode.SPIN_CONFLICT, validationState));
				}
				Optional<CMError> error = validateParticle(validationState, nextParticle, false, dp);
				if (error.isPresent()) {
					return error;
				}

				particleIndex++;
			} else if (cmMicroInstruction.getMicroOp() == CMMicroInstruction.CMMicroOp.VIRTUAL_SPIN_DOWN) {
				final var nextParticle = cmMicroInstruction.getParticle();
				final Result staticCheckResult = particleStaticCheck.apply(nextParticle);
				if (staticCheckResult.isError()) {
					return Optional.of(new CMError(
						dp,
						CMErrorCode.INVALID_PARTICLE,
						validationState,
						staticCheckResult.getErrorMessage()
					));
				}
				final Spin checkSpin = cmMicroInstruction.getCheckSpin();
				boolean okay = validationState.checkSpinAndPush(nextParticle, checkSpin);
				if (!okay) {
					return Optional.of(new CMError(dp, CMErrorCode.SPIN_CONFLICT, validationState));
				}

				Optional<CMError> error = validateParticle(validationState, nextParticle, true, dp);
				if (error.isPresent()) {
					return error;
				}
				particleIndex++;
			} else if (cmMicroInstruction.getMicroOp() == CMMicroInstruction.CMMicroOp.SPIN_DOWN) {
				var particleHash = cmMicroInstruction.getParticleHash();
				var maybeParticle = validationState.checkUpAndPush(particleHash);
				if (maybeParticle.isEmpty()) {
					return Optional.of(new CMError(dp, CMErrorCode.SPIN_CONFLICT, validationState));
				}

				var particle = maybeParticle.get();
				downedParticles.put(cmMicroInstruction.getParticleHash(), particle);
				Optional<CMError> error = validateParticle(validationState, particle, true, dp);
				if (error.isPresent()) {
					return error;
				}
				particleIndex++;
			} else if (cmMicroInstruction.getMicroOp() == CMMicroInstruction.CMMicroOp.PARTICLE_GROUP) {
				if (particleIndex == 0) {
					return Optional.of(
						new CMError(
							DataPointer.ofParticleGroup(particleGroupIndex),
							CMErrorCode.EMPTY_PARTICLE_GROUP,
							validationState
						)
					);
				}

				if (!validationState.isEmpty()) {
					return Optional.of(
						new CMError(
							DataPointer.ofParticleGroup(particleGroupIndex),
							CMErrorCode.UNEQUAL_INPUT_OUTPUT,
							validationState,
							validationState.toString()
						)
					);
				}
				particleGroupIndex++;
				particleIndex = 0;
			} else {
				throw new IllegalStateException("Unknown CM Operation: " + cmMicroInstruction.getMicroOp());
			}
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
	 * @param cmInstruction instruction to validate
	 * @return the first error found, otherwise an empty optional
	 */
	public Optional<CMError> validate(
		CMStore.Transaction txn,
		CMStore cmStore,
		CMInstruction cmInstruction,
		HashCode witness,
		PermissionLevel permissionLevel,
		Map<HashCode, Particle> downedParticles
	) {
		final CMValidationState validationState = new CMValidationState(
			txn,
			cmStore,
			permissionLevel,
			witness,
			cmInstruction.getSignatures()
		);

		return this.validateMicroInstructions(validationState, cmInstruction.getMicroInstructions(), downedParticles);
	}
}
