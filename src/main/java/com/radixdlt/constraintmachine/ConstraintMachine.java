package com.radixdlt.constraintmachine;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Streams;
import com.radixdlt.atoms.ImmutableAtom;
import com.radixdlt.atoms.ParticleGroup;
import com.radixdlt.atoms.Spin;
import com.radixdlt.atoms.SpunParticle;
import com.radixdlt.common.Pair;
import com.radixdlt.constraintmachine.ParticleProcedure.ProcedureResult;
import com.radixdlt.store.CMStore;
import com.radixdlt.store.SpinStateTransitionValidator;
import com.radixdlt.store.SpinStateTransitionValidator.TransitionCheckResult;
import java.util.Stack;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import com.radixdlt.atoms.Particle;
import com.radixdlt.store.CMStores;

import java.util.Objects;
import java.util.stream.Stream;

/**
 * An implementation of a UTXO based constraint machine which uses Radix's atom structure.
 */
public final class ConstraintMachine {
	public static class Builder {
		private UnaryOperator<CMStore> virtualStore;
		private ImmutableList.Builder<KernelConstraintProcedure> kernelConstraintProcedureBuilder = new ImmutableList.Builder<>();
		private Function<Particle, ParticleProcedure> particleProcedures;

		public Builder virtualStore(UnaryOperator<CMStore> virtualStore) {
			this.virtualStore = virtualStore;
			return this;
		}

		public Builder addProcedure(KernelConstraintProcedure kernelConstraintProcedure) {
			kernelConstraintProcedureBuilder.add(kernelConstraintProcedure);
			return this;
		}

		public Builder setParticleProcedures(Function<Particle, ParticleProcedure> particleProcedures) {
			this.particleProcedures = particleProcedures;
			return this;
		}

		public ConstraintMachine build() {
			if (virtualStore == null) {
				virtualStore = UnaryOperator.identity();
			}

			return new ConstraintMachine(
				virtualStore,
				kernelConstraintProcedureBuilder.build(),
				particleProcedures
			);
		}
	}

	private final UnaryOperator<CMStore> virtualStore;
	private final ImmutableList<KernelConstraintProcedure> kernelConstraintProcedures;
	private final Function<Particle, ParticleProcedure> particleProcedures;
	private final CMStore localEngineStore;

	ConstraintMachine(
		UnaryOperator<CMStore> virtualStore,
		ImmutableList<KernelConstraintProcedure> kernelConstraintProcedures,
		Function<Particle, ParticleProcedure> particleProcedures
	) {
		Objects.requireNonNull(virtualStore);

		this.virtualStore = Objects.requireNonNull(virtualStore);
		this.localEngineStore = this.virtualStore.apply(CMStores.empty());
		this.kernelConstraintProcedures = kernelConstraintProcedures;
		this.particleProcedures = particleProcedures;
	}

	private boolean inputExecute(ParticleProcedure particleProcedure, Particle input, AtomMetadata metadata, Stack<Pair<Particle, Object>> outputs) {
		AtomicReference<Object> inputData = new AtomicReference<>();

		ProcedureResult action;
		do {
			if (outputs.empty()) {
				action = ProcedureResult.ERROR;
				break;
			}
			Pair<Particle, Object> top = outputs.pop();
			Particle output = top.getFirst();
			AtomicReference<Object> outputData = new AtomicReference<>(top.getSecond());
			action = particleProcedure.execute(input, inputData, output, outputData, metadata);

			switch (action) {
				case POP_INPUT:
				case ERROR:
					outputs.push(Pair.of(output, outputData.get()));
			}
		} while (action.equals(ProcedureResult.POP_OUTPUT));

		return action != ProcedureResult.ERROR;
	}

	private Stream<ProcedureError> validate(ParticleGroup group, AtomMetadata metadata) {
		final Stack<Pair<Particle, Object>> outputs = new Stack<>();

		for (int i = group.getParticleCount() - 1; i >= 0; i--) {
			SpunParticle sp = group.getSpunParticle(i);
			Particle p = sp.getParticle();
			ParticleProcedure particleProcedure = this.particleProcedures.apply(p);
			if (sp.getSpin() == Spin.DOWN) {
				if (particleProcedure == null || !this.inputExecute(particleProcedure, p, metadata, outputs)) {
					return Stream.of(ProcedureError.of("Input particle " + p + " failed. Output stack: " + outputs));
				}
			} else {
				if (particleProcedure == null || !particleProcedure.outputExecute(p, metadata)) {
					outputs.push(Pair.of(p, null));
				}
			}
		}

		if (!outputs.empty()) {
			return Stream.of(ProcedureError.of("Output particle stack not empty: " + outputs));
		}

		return Stream.empty();
	}

	/**
	 * Validates an atom and calculates the necessary state checks and post-validation
	 * write logic.
	 *
	 * @param cmAtom atom to validate
	 * @param getAllErrors if true, returns all errors, otherwise, fails fast
	 * and just returns the first error
	 * @return results of validation, including any errors, warnings, and post-validation write logic
	 */
	public ImmutableSet<CMError> validate(CMAtom cmAtom, boolean getAllErrors) {
		// "Segfaults" or particles which should not exist
		final Stream<CMError> unknownParticleErrors = cmAtom.getParticles().stream()
			.filter(p -> !localEngineStore.getSpin(p.getParticle()).isPresent())
			.map(p -> new CMError(p.getDataPointer(), CMErrorCode.UNKNOWN_PARTICLE));

		// Virtual particle state checks
		// TODO: Is this better suited at the state check pipeline?
		final Stream<CMError> virtualParticleErrors = cmAtom.getParticles().stream()
			.filter(p -> {
				Particle particle = p.getParticle();
				Spin nextSpin = p.getNextSpin();
				TransitionCheckResult result = SpinStateTransitionValidator.checkParticleTransition(
					particle,
					nextSpin, localEngineStore
				);

				return result.equals(TransitionCheckResult.CONFLICT);
			})
			.map(p ->  new CMError(p.getDataPointer(), CMErrorCode.INTERNAL_SPIN_CONFLICT));

		// "Kernel" checks
		final Stream<CMError> kernelErrs = kernelConstraintProcedures.stream()
			.flatMap(kernelProcedure -> kernelProcedure.validate(cmAtom))
			.map(CMErrors::fromKernelProcedureError);

		// "Application" checks
		final ImmutableAtom atom = cmAtom.getAtom();
		final AtomMetadata metadata = new AtomMetadataFromAtom(atom);
		final Stream<CMError> applicationErrs = Streams.mapWithIndex(atom.particleGroups(), (group, i) ->
			this.validate(group, metadata).map(issue -> CMErrors.fromProcedureError(issue, (int) i))
		).flatMap(i -> i);

		final Stream<CMError> errorStream = Streams.concat(
			unknownParticleErrors,
			virtualParticleErrors,
			kernelErrs,
			applicationErrs
		);

		return getAllErrors
			? errorStream.collect(ImmutableSet.toImmutableSet())
			: errorStream.findAny().map(ImmutableSet::of).orElse(ImmutableSet.of());
	}

	public UnaryOperator<CMStore> getVirtualStore() {
		return this.virtualStore;
	}
}
