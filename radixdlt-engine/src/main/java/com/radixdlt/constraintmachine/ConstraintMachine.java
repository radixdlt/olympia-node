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
import com.radixdlt.atomos.Result;
import com.radixdlt.identifiers.EUID;
import com.radixdlt.constraintmachine.WitnessValidator.WitnessValidatorResult;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.crypto.ECDSASignature;
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
		private final HashCode witness;
		private final Map<EUID, ECDSASignature> signatures;
		private final Map<ECPublicKey, Boolean> isSignedByCache = new HashMap<>();
		private final CMStore store;

		CMValidationState(
			CMStore store,
			PermissionLevel permissionLevel,
			HashCode witness,
			Map<EUID, ECDSASignature> signatures
		) {
			this.store = store;
			this.permissionLevel = permissionLevel;
			this.currentSpins = new HashMap<>();
			this.witness = witness;
			this.signatures = signatures;
		}

		public void setCurrentTransitionToken(TransitionToken currentTransitionToken) {
			this.currentTransitionToken = currentTransitionToken;
		}

		public boolean checkSpin(Particle particle, Spin spin) {
			final Spin currentSpin;
			if (currentSpins.containsKey(particle)) {
				currentSpin = currentSpins.get(particle);
			} else {
				currentSpin = store.getSpin(particle);
				currentSpins.put(particle, currentSpin);
			}

			return currentSpin.equals(spin);
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

		boolean push(Particle p) {
			final Spin curSpin = currentSpins.get(p);
			final Spin nextSpin = SpinStateMachine.next(curSpin);
			currentSpins.put(p, nextSpin);
			return nextSpin == Spin.DOWN;
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
	Optional<CMError> validateMicroInstructions(CMValidationState validationState, List<CMMicroInstruction> microInstructions) {
		long particleGroupIndex = 0;
		long particleIndex = 0;

		for (CMMicroInstruction cmMicroInstruction : microInstructions) {
			final DataPointer dp = DataPointer.ofParticle(particleGroupIndex, particleIndex);
			switch (cmMicroInstruction.getMicroOp()) {
				case CHECK_NEUTRAL_THEN_UP:
				case CHECK_UP_THEN_DOWN:
					final Particle nextParticle;
					if (cmMicroInstruction.getMicroOp() == CMMicroInstruction.CMMicroOp.CHECK_NEUTRAL_THEN_UP) {
						nextParticle = cmMicroInstruction.getParticle();
					} else {
						if (cmMicroInstruction.getParticle() != null) {
							// Virtual UP particle
							nextParticle = cmMicroInstruction.getParticle();
						} else {
							var particleHash = cmMicroInstruction.getParticleHash();
							var maybeParticle = validationState.store.loadUpParticle(particleHash);
							if (maybeParticle.isEmpty()) {
								return Optional.of(new CMError(dp, CMErrorCode.SPIN_CONFLICT, validationState));
							}
							nextParticle = maybeParticle.get();
						}
					}
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
					boolean noConflict = validationState.checkSpin(nextParticle, checkSpin);
					if (!noConflict) {
						return Optional.of(new CMError(dp, CMErrorCode.SPIN_CONFLICT, validationState));
					}

					final boolean isInput = validationState.push(nextParticle);
					Optional<CMError> error = validateParticle(validationState, nextParticle, isInput, dp);
					if (error.isPresent()) {
						return error;
					}
					particleIndex++;
					break;
				case PARTICLE_GROUP:
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
					break;
				default:
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
	public Optional<CMError> validate(CMStore cmStore, CMInstruction cmInstruction, HashCode witness, PermissionLevel permissionLevel) {
		final CMValidationState validationState = new CMValidationState(
			cmStore,
			permissionLevel,
			witness,
			cmInstruction.getSignatures()
		);

		return this.validateMicroInstructions(validationState, cmInstruction.getMicroInstructions());
	}
}
