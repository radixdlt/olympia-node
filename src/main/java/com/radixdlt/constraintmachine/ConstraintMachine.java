package com.radixdlt.constraintmachine;

import com.radixdlt.atomos.Result;
import com.radixdlt.atoms.DataPointer;
import com.radixdlt.atoms.Spin;
import com.radixdlt.atoms.SpunParticle;
import com.radixdlt.common.EUID;
import com.radixdlt.constraintmachine.TransitionProcedure.ProcedureResult;
import com.radixdlt.constraintmachine.WitnessValidator.WitnessValidatorResult;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.crypto.ECSignature;
import com.radixdlt.crypto.Hash;
import com.radixdlt.store.CMStore;
import com.radixdlt.store.SpinStateMachine;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import com.radixdlt.atoms.Particle;
import com.radixdlt.store.CMStores;

import java.util.Objects;

/**
 * An implementation of a UTXO based constraint machine which uses Radix's atom structure.
 */
public final class ConstraintMachine {
	public static class Builder {
		private UnaryOperator<CMStore> virtualStore;
		private Function<Particle, Result> particleStaticCheck;
		private BiFunction<Particle, Particle, TransitionProcedure<Particle, Particle>> particleProcedures;
		private BiFunction<Particle, Particle, WitnessValidator<Particle, Particle>> witnessValidators;

		public Builder virtualStore(UnaryOperator<CMStore> virtualStore) {
			this.virtualStore = virtualStore;
			return this;
		}

		public Builder setParticleStaticCheck(Function<Particle, Result> particleStaticCheck) {
			this.particleStaticCheck = particleStaticCheck;
			return this;
		}


		public Builder setParticleProcedures(BiFunction<Particle, Particle, TransitionProcedure<Particle, Particle>> particleProcedures) {
			this.particleProcedures = particleProcedures;
			return this;
		}

		public Builder setWitnessValidators(BiFunction<Particle, Particle, WitnessValidator<Particle, Particle>> witnessValidators) {
			this.witnessValidators = witnessValidators;
			return this;
		}


		public ConstraintMachine build() {
			if (virtualStore == null) {
				virtualStore = UnaryOperator.identity();
			}

			return new ConstraintMachine(
				virtualStore,
				particleStaticCheck,
				particleProcedures,
				witnessValidators
			);
		}
	}

	private final UnaryOperator<CMStore> virtualStore;
	private final Function<Particle, Result> particleStaticCheck;
	private final BiFunction<Particle, Particle, TransitionProcedure<Particle, Particle>> particleProcedures;
	private final BiFunction<Particle, Particle, WitnessValidator<Particle, Particle>> witnessValidators;
	private final CMStore localEngineStore;

	ConstraintMachine(
		UnaryOperator<CMStore> virtualStore,
		Function<Particle, Result> particleStaticCheck,
		BiFunction<Particle, Particle, TransitionProcedure<Particle, Particle>> particleProcedures,
		BiFunction<Particle, Particle, WitnessValidator<Particle, Particle>> witnessValidators
	) {
		Objects.requireNonNull(virtualStore);

		this.virtualStore = Objects.requireNonNull(virtualStore);
		this.localEngineStore = this.virtualStore.apply(CMStores.empty());
		this.particleStaticCheck = particleStaticCheck;
		this.particleProcedures = particleProcedures;
		this.witnessValidators = witnessValidators;
	}

	static final class CMValidationState {
		private SpunParticle spunParticleRemaining = null;
		private Object particleRemainingUsed = null;
		private final Map<Particle, Spin> currentSpins;
		private final Hash witness;
		private final Map<EUID, ECSignature> signatures;
		private final Map<ECPublicKey, Boolean> isSignedByCache = new HashMap<>();

		CMValidationState(Hash witness, Map<EUID, ECSignature> signatures) {
			this.currentSpins = new HashMap<>();
			this.witness = witness;
			this.signatures = signatures;
		}

		public boolean checkSpin(Particle particle, Spin spin) {
			if (currentSpins.containsKey(particle)) {
				return false;
			}

			this.currentSpins.put(particle, spin);
			return true;
		}

		public boolean isSignedBy(ECPublicKey publicKey) {
			return this.isSignedByCache.computeIfAbsent(publicKey, this::verifySignedWith);
		}

		private boolean verifySignedWith(ECPublicKey publicKey) {
			if (signatures == null || signatures.isEmpty() || witness == null) {
				return false;
			}

			final ECSignature signature = signatures.get(publicKey.getUID());
			return signature != null && publicKey.verify(witness, signature);
		}

		Spin push(Particle p) {
			final Spin curSpin = currentSpins.get(p);
			final Spin nextSpin = SpinStateMachine.next(curSpin);
			currentSpins.put(p, nextSpin);
			return nextSpin;
		}

		Particle getCurParticle() {
			return spunParticleRemaining == null ? null : spunParticleRemaining.getParticle();
		}

		boolean spinClashes(Spin spin) {
			return spunParticleRemaining != null && spunParticleRemaining.getSpin() == spin;
		}

		Object getInputUsed() {
			return spunParticleRemaining != null && spunParticleRemaining.getSpin() == Spin.DOWN ? particleRemainingUsed : null;
		}

		Object getOutputUsed() {
			return spunParticleRemaining != null && spunParticleRemaining.getSpin() == Spin.UP ? particleRemainingUsed : null;
		}

		void pop() {
			this.spunParticleRemaining = null;
			this.particleRemainingUsed = null;
		}

		void popAndReplace(SpunParticle spunParticle, Object particleRemainingUsed) {
			this.spunParticleRemaining = spunParticle;
			this.particleRemainingUsed = particleRemainingUsed;
		}

		void updateUsed(Object particleRemainingUsed) {
			this.particleRemainingUsed = particleRemainingUsed;
		}

		boolean isEmpty() {
			return this.spunParticleRemaining == null;
		}
	}

	/**
	 * Executes a transition procedure given the next spun particle and a current validation state.
	 *
	 * @param nextSpun the next spun particle
	 * @param dp pointer of the next spun particle
	 * @param validationState local state of validation
	 * @return the first error found, otherwise an empty optional
	 */
	Optional<CMError> validateParticle(CMValidationState validationState, SpunParticle nextSpun, DataPointer dp) {
		final Particle nextParticle = nextSpun.getParticle();
		final Particle curParticle = validationState.getCurParticle();

		if (validationState.spinClashes(nextSpun.getSpin())) {
			return Optional.of(
				new CMError(
					dp,
					CMErrorCode.PARTICLE_REGISTER_SPIN_CLASH,
					validationState
				)
			);
		}

		final Particle inputParticle = nextSpun.getSpin() == Spin.DOWN ? nextParticle : curParticle;
		final Particle outputParticle = nextSpun.getSpin() == Spin.DOWN ? curParticle : nextParticle;
		final TransitionProcedure<Particle, Particle> transitionProcedure = this.particleProcedures.apply(inputParticle, outputParticle);

		if (transitionProcedure == null) {
			if (inputParticle == null || outputParticle == null) {
				validationState.popAndReplace(nextSpun, null);
				return Optional.empty();
			}

			return Optional.of(
				new CMError(
					dp,
					CMErrorCode.MISSING_TRANSITION_PROCEDURE,
					validationState
				)
			);
		}

		final ProcedureResult result = transitionProcedure.execute(
			inputParticle,
			validationState.getInputUsed(),
			outputParticle,
			validationState.getOutputUsed()
		);
		switch (result.getCmAction()) {
			case POP_INPUT:
				if (nextSpun.getSpin() == Spin.UP) {
					validationState.popAndReplace(nextSpun, result.getUsed());
				} else {
					validationState.updateUsed(result.getUsed());
				}
				break;
			case POP_OUTPUT:
				if (nextSpun.getSpin() == Spin.DOWN) {
					validationState.popAndReplace(nextSpun, result.getUsed());
				} else {
					validationState.updateUsed(result.getUsed());
				}
				break;
			case POP_INPUT_OUTPUT:
				if (result.getUsed() != null) {
					throw new IllegalStateException("POP_INPUT_OUTPUT must output null");
				}
				validationState.pop();
				break;
			case ERROR:
				return Optional.of(
					new CMError(
						dp,
						CMErrorCode.TRANSITION_ERROR,
						validationState,
						result.getErrorMessage()
					)
				);
		}

		final WitnessValidator<Particle, Particle> witnessValidator = this.witnessValidators.apply(inputParticle, outputParticle);
		if (witnessValidator == null) {
			throw new IllegalStateException("No witness validator for: " + inputParticle + " -> " + outputParticle);
		}
		final WitnessValidatorResult witnessValidatorResult = witnessValidator.validate(
			result.getCmAction(),
			inputParticle,
			outputParticle,
			validationState::isSignedBy
		);
		if (witnessValidatorResult.isError()) {
			return Optional.of(
				new CMError(
					dp,
					CMErrorCode.WITNESS_ERROR,
					validationState,
					witnessValidatorResult.getErrorMessage()
				)
			);
		}

		return Optional.empty();
	}

	/**
	 * Executes transition procedures and witness validators in a particle group and validates
	 * that the particle group is well formed.
	 *
	 * @return the first error found, otherwise an empty optional
	 */
	Optional<CMError> validateMicroInstructions(CMValidationState validationState, List<CMMicroInstruction> microInstructions) {
		int particleGroupIndex = 0;
		int particleIndex = 0;

		for (CMMicroInstruction cmMicroInstruction : microInstructions) {
			final DataPointer dp = DataPointer.ofParticle(particleGroupIndex, particleIndex);
			switch (cmMicroInstruction.getMicroOp()) {
				case CHECK_NEUTRAL:
				case CHECK_UP:
					final Result staticCheckResult = particleStaticCheck.apply(cmMicroInstruction.getParticle());
					if (staticCheckResult.isError()) {
						return Optional.of(new CMError(dp, CMErrorCode.INVALID_PARTICLE, validationState, staticCheckResult.getErrorMessage()));
					}

					final Spin curSpin = localEngineStore.getSpin(cmMicroInstruction.getParticle());
					final Spin checkSpin = cmMicroInstruction.getCheckSpin();

					// Virtual particle state checks
					// TODO: Is this better suited at the state check pipeline?
					if (SpinStateMachine.isBefore(checkSpin, curSpin)) {
						return Optional.of(new CMError(dp, CMErrorCode.INTERNAL_SPIN_CONFLICT));
					}

					boolean updated = validationState.checkSpin(cmMicroInstruction.getParticle(), checkSpin);
					if (!updated) {
						return Optional.of(new CMError(dp, CMErrorCode.INTERNAL_SPIN_CONFLICT));
					}
					break;
				case PUSH:
					final Particle nextParticle = cmMicroInstruction.getParticle();
					final Spin nextSpin = validationState.push(nextParticle);
					final SpunParticle nextSpun = SpunParticle.of(nextParticle, nextSpin);
					Optional<CMError> error = validateParticle(validationState, nextSpun, dp);
					if (error.isPresent()) {
						return error;
					}
					particleIndex++;
					break;
				case PARTICLE_GROUP:
					if (!validationState.isEmpty()) {
						return Optional.of(
							new CMError(
								DataPointer.ofParticleGroup(particleGroupIndex),
								CMErrorCode.UNEQUAL_INPUT_OUTPUT,
								validationState
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
				CMErrorCode.MISSING_PARTICLE_GROUP
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
	public Optional<CMError> validate(CMInstruction cmInstruction) {
		final CMValidationState validationState = new CMValidationState(
			cmInstruction.getWitness(),
			cmInstruction.getSignatures()
		);

		return this.validateMicroInstructions(validationState, cmInstruction.getMicroInstructions());
	}

	/**
	 * Retrieves the virtual layer used by this Constraint Machine
	 */
	public UnaryOperator<CMStore> getVirtualStore() {
		return this.virtualStore;
	}
}
