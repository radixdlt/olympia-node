package com.radixdlt.constraintmachine;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Streams;
import com.radixdlt.atomos.AtomOSKernel.AtomKernelCompute;
import com.radixdlt.atoms.ImmutableAtom;
import java.util.Map;
import java.util.function.UnaryOperator;
import com.radixdlt.atoms.IndexedSpunParticle;
import com.radixdlt.atoms.Particle;
import com.radixdlt.store.CMStore;
import com.radixdlt.store.CMStores;

import java.util.Objects;
import java.util.stream.Stream;

/**
 * An implementation of a UTXO based constraint machine which uses Radix's atom structure.
 */
public final class ConstraintMachine {
	public static class Builder {
		private UnaryOperator<CMStore> stateTransformer;

		private ImmutableList.Builder<KernelConstraintProcedure> kernelConstraintProcedureBuilder = new ImmutableList.Builder<>();
		private ImmutableMap.Builder<String, AtomKernelCompute> kernelComputeBuilder = new ImmutableMap.Builder<>();

		private ImmutableList.Builder<ConstraintProcedure> constraintProcedureBuilder = new ImmutableList.Builder<>();

		public Builder stateTransformer(UnaryOperator<CMStore> stateTransformer) {
			this.stateTransformer = stateTransformer;
			return this;
		}

		public Builder addProcedure(KernelConstraintProcedure kernelConstraintProcedure) {
			kernelConstraintProcedureBuilder.add(kernelConstraintProcedure);
			return this;
		}

		public Builder addProcedure(ConstraintProcedure constraintProcedure) {
			constraintProcedureBuilder.add(constraintProcedure);
			return this;
		}

		public Builder addCompute(String key, AtomKernelCompute atomKernelCompute) {
			kernelComputeBuilder.put(key, atomKernelCompute);
			return this;
		}

		public ConstraintMachine build() {
			if (stateTransformer == null) {
				stateTransformer = UnaryOperator.identity();
			}

			return new ConstraintMachine(
				stateTransformer,
				kernelConstraintProcedureBuilder.build(),
				kernelComputeBuilder.build(),
				constraintProcedureBuilder.build()
			);
		}
	}

	private final UnaryOperator<CMStore> stateStoreTransformer;
	private final ImmutableList<KernelConstraintProcedure> kernelConstraintProcedures;
	private final ImmutableMap<String, AtomKernelCompute> kernelComputes;
	private final ImmutableList<ConstraintProcedure> applicationConstraintProcedures;
	private final CMStore localCMStore;

	ConstraintMachine(
		UnaryOperator<CMStore> transformer,
		ImmutableList<KernelConstraintProcedure> kernelConstraintProcedures,
		ImmutableMap<String, AtomKernelCompute> kernelComputes,
		ImmutableList<ConstraintProcedure> applicationConstraintProcedures
	) {
		Objects.requireNonNull(transformer);

		this.stateStoreTransformer = Objects.requireNonNull(transformer);
		this.localCMStore = this.stateStoreTransformer.apply(CMStores.empty());
		this.kernelConstraintProcedures = kernelConstraintProcedures;
		this.kernelComputes = kernelComputes;
		this.applicationConstraintProcedures = applicationConstraintProcedures;
	}

	/**
	 * Validates an atom and calculates the necessary state checks and post-validation
	 * write logic.
	 *
	 * @param atom atom to validate
	 * @param getAllErrors if true, returns all errors, otherwise, fails fast
	 * and just returns the first error
	 * @return results of validation, including any errors, warnings, and post-validation write logic
	 */
	public CMResult validate(ImmutableAtom atom, boolean getAllErrors) {
		// "Hardware" checks
		final Map<Particle, ImmutableList<IndexedSpunParticle>> spunParticles = ConstraintMachineUtils.getTransitionsByParticle(atom);
		final Stream<CMError> badSpinErrs = spunParticles.entrySet().stream()
			.flatMap(e -> ConstraintMachineUtils.checkInternalSpins(e.getValue(), localCMStore));
		final Stream<CMError> hwErrs = Streams.concat(
			ConstraintMachineUtils.checkParticleGroupsNotEmpty(atom),
			ConstraintMachineUtils.checkParticleTransitionsUniqueInGroup(atom),
			badSpinErrs
		);

		// "Kernel" checks
		final ImmutableList<CMParticle> cmParticles =
			spunParticles.entrySet().stream()
				.map(e -> new CMParticle(e.getKey(), e.getValue()))
				.collect(ImmutableList.toImmutableList());

		final ImmutableMap.Builder<String, Object> atomCompute = new ImmutableMap.Builder<>();
		kernelComputes.forEach((key, c) -> atomCompute.put(key, c.compute(atom)));

		final CMAtom cmAtom = new CMAtom(atom, cmParticles, atomCompute.build());
		final Stream<CMError> kernelErrs = kernelConstraintProcedures.stream()
			.flatMap(kernelProcedure -> kernelProcedure.validate(cmAtom))
			.map(CMErrors::fromKernelProcedureError);

		// "Application" checks
		final AtomMetadata metadata = new AtomMetadataFromAtom(atom);
		final Stream<CMError> applicationErrs = Streams.mapWithIndex(atom.particleGroups(), (group, i) ->
			applicationConstraintProcedures.stream()
				.flatMap(procedure -> procedure.validate(group, metadata))
				.map(issue -> CMErrors.fromProcedureError(issue, (int) i))
		).flatMap(i -> i);

		final Stream<CMError> errorStream = Streams.concat(
			hwErrs,
			kernelErrs,
			applicationErrs
		);

		final ImmutableSet<CMError> errors = getAllErrors
			? errorStream.collect(ImmutableSet.toImmutableSet())
			: errorStream.findAny().map(ImmutableSet::of).orElse(ImmutableSet.of());

		return new CMResult(errors, cmAtom);
	}

	public CMStore virtualize(CMStore base) {
		return stateStoreTransformer.apply(base);
	}
}
