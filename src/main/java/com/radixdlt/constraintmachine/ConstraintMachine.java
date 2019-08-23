package com.radixdlt.constraintmachine;

import com.radixdlt.atoms.DataPointer;
import com.radixdlt.atoms.Spin;
import com.radixdlt.atoms.SpunParticle;
import com.radixdlt.constraintmachine.TransitionProcedure.ProcedureResult;
import com.radixdlt.constraintmachine.WitnessValidator.WitnessValidatorResult;
import com.radixdlt.store.CMStore;
import com.radixdlt.store.SpinStateMachine;
import com.radixdlt.store.SpinStateTransitionValidator;
import com.radixdlt.store.SpinStateTransitionValidator.TransitionCheckResult;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.UnaryOperator;
import com.radixdlt.atoms.Particle;
import com.radixdlt.store.CMStores;

import java.util.Objects;
import java.util.stream.Collectors;

/**
 * An implementation of a UTXO based constraint machine which uses Radix's atom structure.
 */
public final class ConstraintMachine {
	public static class Builder {
		private UnaryOperator<CMStore> virtualStore;
		private BiFunction<Particle, Particle, TransitionProcedure<Particle, Particle>> particleProcedures;
		private BiFunction<Particle, Particle, WitnessValidator<Particle, Particle>> witnessValidators;

		public Builder virtualStore(UnaryOperator<CMStore> virtualStore) {
			this.virtualStore = virtualStore;
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
				particleProcedures,
				witnessValidators
			);
		}
	}

	private final UnaryOperator<CMStore> virtualStore;
	private final BiFunction<Particle, Particle, TransitionProcedure<Particle, Particle>> particleProcedures;
	private final BiFunction<Particle, Particle, WitnessValidator<Particle, Particle>> witnessValidators;
	private final CMStore localEngineStore;

	ConstraintMachine(
		UnaryOperator<CMStore> virtualStore,
		BiFunction<Particle, Particle, TransitionProcedure<Particle, Particle>> particleProcedures,
		BiFunction<Particle, Particle, WitnessValidator<Particle, Particle>> witnessValidators
	) {
		Objects.requireNonNull(virtualStore);

		this.virtualStore = Objects.requireNonNull(virtualStore);
		this.localEngineStore = this.virtualStore.apply(CMStores.empty());
		this.particleProcedures = particleProcedures;
		this.witnessValidators = witnessValidators;
	}

	static final class CMValidationState {
		private SpunParticle spunParticleRemaining = null;
		private Object particleRemainingUsed = null;
		private final Map<Particle, Spin> currentSpins;

		CMValidationState(Map<Particle, Spin> initialSpins) {
			this.currentSpins = initialSpins;
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
	 * @param metadata metadata associated with the atom
	 * @param validationState local state of validation
	 * @return the first error found, otherwise an empty optional
	 */
	Optional<CMError> validateParticle(SpunParticle nextSpun, DataPointer dp, AtomMetadata metadata, CMValidationState validationState) {
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
			metadata
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
	 * @param group the particle group
	 * @param groupIndex the index of the particle group
	 * @param metadata atom meta data
	 * @return the first error found, otherwise an empty optional
	 */
	Optional<CMError> validateParticleGroup(CMValidationState validationState, List<Particle> group, long groupIndex, AtomMetadata metadata) {

		for (int i = 0; i < group.size(); i++) {
			final DataPointer dp = DataPointer.ofParticle((int) groupIndex, i);
			final Particle nextParticle = group.get(i);
			final Spin nextSpin = validationState.push(nextParticle);
			final SpunParticle nextSpun = SpunParticle.of(nextParticle, nextSpin);

			Optional<CMError> error = validateParticle(nextSpun, dp, metadata, validationState);
			if (error.isPresent()) {
				return error;
			}
		}

		if (!validationState.isEmpty()) {
			return Optional.of(
				new CMError(
					DataPointer.ofParticleGroup((int) groupIndex),
					CMErrorCode.UNEQUAL_INPUT_OUTPUT,
					validationState
				)
			);
		}

		return Optional.empty();
	}

	/**
	 * Validates an atom and calculates the necessary state checks and post-validation
	 * write logic.
	 *
	 * @param cmInstruction instruction to validate
	 * @return the first error found, otherwise an empty optional
	 */
	public Optional<CMError> validate(CMInstruction cmInstruction) {
		// "Segfaults" or particles which should not exist
		final Optional<CMError> unknownParticleError = cmInstruction.getParticles().stream()
			.filter(p -> !localEngineStore.getSpin(p.getParticle()).isPresent())
			.map(p -> new CMError(p.getDataPointer(), CMErrorCode.UNKNOWN_PARTICLE))
			.findFirst();

		if (unknownParticleError.isPresent()) {
			return unknownParticleError;
		}

		// Virtual particle state checks
		// TODO: Is this better suited at the state check pipeline?
		final Optional<CMError> virtualParticleError = cmInstruction.getParticles().stream()
			.filter(p -> {
				Particle particle = p.getParticle();
				Spin nextSpin = p.getNextSpin();
				TransitionCheckResult result = SpinStateTransitionValidator.checkParticleTransition(
					particle,
					nextSpin, localEngineStore
				);

				return result.equals(TransitionCheckResult.CONFLICT);
			})
			.map(p ->  new CMError(p.getDataPointer(), CMErrorCode.INTERNAL_SPIN_CONFLICT))
			.findFirst();

		if (virtualParticleError.isPresent()) {
			return virtualParticleError;
		}

		// Transition checks
		final AtomMetadata metadata = new AtomMetadataFromAtom(cmInstruction);
		final Map<Particle, Spin> initialSpins = cmInstruction.getParticles().stream().collect(Collectors.toMap(
			CMParticle::getParticle,
			CMParticle::getCheckSpin
		));
		final CMValidationState validationState = new CMValidationState(initialSpins);
		for (int i = 0; i < cmInstruction.getParticlePushes().size(); i++) {
			final Optional<CMError> error = this.validateParticleGroup(
				validationState,
				cmInstruction.getParticlePushes().get(i),
				i,
				metadata
			);
			if (error.isPresent()) {
				return error;
			}
		}

		return Optional.empty();
	}

	/**
	 * Retrieves the virtual layer used by this Constraint Machine
	 */
	public UnaryOperator<CMStore> getVirtualStore() {
		return this.virtualStore;
	}
}
