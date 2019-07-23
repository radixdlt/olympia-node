package com.radixdlt.constraintmachine;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Streams;
import com.radixdlt.atomos.AtomOSKernel.AtomKernelCompute;
import com.radixdlt.atoms.ImmutableAtom;
import com.radixdlt.atoms.Spin;
import com.radixdlt.engine.ValidationResult;
import com.radixdlt.store.SpinStateTransitionValidator;
import com.radixdlt.store.SpinStateTransitionValidator.TransitionCheckResult;
import java.util.function.UnaryOperator;
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
		private UnaryOperator<CMStore> virtualStore;

		private ImmutableList.Builder<KernelConstraintProcedure> kernelConstraintProcedureBuilder = new ImmutableList.Builder<>();
		private ImmutableMap.Builder<String, AtomKernelCompute> kernelComputeBuilder = new ImmutableMap.Builder<>();

		private ImmutableList.Builder<ConstraintProcedure> constraintProcedureBuilder = new ImmutableList.Builder<>();

		public Builder virtualStore(UnaryOperator<CMStore> virtualStore) {
			this.virtualStore = virtualStore;
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
			if (virtualStore == null) {
				virtualStore = UnaryOperator.identity();
			}

			return new ConstraintMachine(
				virtualStore,
				kernelConstraintProcedureBuilder.build(),
				kernelComputeBuilder.build(),
				constraintProcedureBuilder.build()
			);
		}
	}

	private final UnaryOperator<CMStore> virtualStore;
	private final ImmutableList<KernelConstraintProcedure> kernelConstraintProcedures;
	private final ImmutableMap<String, AtomKernelCompute> kernelComputes;
	private final ImmutableList<ConstraintProcedure> applicationConstraintProcedures;
	private final CMStore localCMStore;

	ConstraintMachine(
		UnaryOperator<CMStore> virtualStore,
		ImmutableList<KernelConstraintProcedure> kernelConstraintProcedures,
		ImmutableMap<String, AtomKernelCompute> kernelComputes,
		ImmutableList<ConstraintProcedure> applicationConstraintProcedures
	) {
		Objects.requireNonNull(virtualStore);

		this.virtualStore = Objects.requireNonNull(virtualStore);
		this.localCMStore = this.virtualStore.apply(CMStores.empty());
		this.kernelConstraintProcedures = kernelConstraintProcedures;
		this.kernelComputes = kernelComputes;
		this.applicationConstraintProcedures = applicationConstraintProcedures;
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
	public ValidationResult validate(CMAtom cmAtom, boolean getAllErrors) {
		// "Segfaults" or particles which should not exist
		final Stream<CMError> unknownParticleErrors = cmAtom.getParticles().stream()
			.filter(p -> !localCMStore.getSpin(p.getParticle()).isPresent())
			.map(p -> new CMError(p.getDataPointer(), CMErrorCode.UNKNOWN_PARTICLE));

		// Virtual particle state checks
		// TODO: Is this better suited at the state check pipeline?
		final Stream<CMError> virtualParticleErrors = cmAtom.getParticles().stream()
			.filter(p -> {
				Particle particle = p.getParticle();
				Spin nextSpin = p.getNextSpin();
				TransitionCheckResult result = SpinStateTransitionValidator.checkParticleTransition(
					particle,
					nextSpin,
					localCMStore
				);

				return result.equals(TransitionCheckResult.CONFLICT);
			})
			.map(p ->  new CMError(p.getDataPointer(), CMErrorCode.INTERNAL_SPIN_CONFLICT));

		final ImmutableAtom atom = cmAtom.getAtom();
		final ImmutableMap.Builder<String, Object> atomCompute = new ImmutableMap.Builder<>();
		kernelComputes.forEach((key, c) -> atomCompute.put(key, c.compute(atom)));

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
			unknownParticleErrors,
			virtualParticleErrors,
			kernelErrs,
			applicationErrs
		);

		final ImmutableSet<CMError> errors = getAllErrors
			? errorStream.collect(ImmutableSet.toImmutableSet())
			: errorStream.findAny().map(ImmutableSet::of).orElse(ImmutableSet.of());

		if (!errors.isEmpty()) {
			return acceptor -> acceptor.onError(errors);
		} else {
			return acceptor -> acceptor.onSuccess(atomCompute.build());
		}
	}

	public UnaryOperator<CMStore> getVirtualStore() {
		return this.virtualStore;
	}
}
