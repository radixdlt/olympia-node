package com.radixdlt.atomos;

import com.google.common.collect.ImmutableSet;
import com.radixdlt.atoms.Particle;
import com.radixdlt.common.Pair;
import com.radixdlt.constraintmachine.AtomMetadata;
import com.radixdlt.constraintmachine.TransitionProcedure;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiPredicate;
import java.util.function.Function;

public class RRIResourceCombinedCreation<T extends Particle, U extends Particle> implements TransitionProcedure {
	private final Class<T> particleClass0;
	private final Function<T, RRI> rriMapper0;
	private final Class<U> particleClass1;
	private final Function<U, RRI> rriMapper1;
	private final BiPredicate<T, U> combinedCheck;

	public RRIResourceCombinedCreation(
		Class<T> particleClass0,
		Function<T, RRI> rriMapper0,
		Class<U> particleClass1,
		Function<U, RRI> rriMapper1,
		BiPredicate<T, U> combinedCheck
	) {
		this.particleClass0 = particleClass0;
		this.rriMapper0 = rriMapper0;
		this.particleClass1 = particleClass1;
		this.rriMapper1 = rriMapper1;
		this.combinedCheck = combinedCheck;
	}

	@Override
	public ImmutableSet<Pair<Class<? extends Particle>, Class<? extends Particle>>> supports() {
		return ImmutableSet.of(
			Pair.of(RRIParticle.class, particleClass0),
			Pair.of(RRIParticle.class, particleClass1)
		);
	}

	@Override
	public ProcedureResult execute(
		Particle inputParticle,
		AtomicReference<Object> inputData,
		Particle outputParticle,
		AtomicReference<Object> outputData
	) {
		RRIParticle rriParticle = (RRIParticle) inputParticle;

		if (inputData.get() == null) {
			if (!outputParticle.getClass().equals(particleClass0)) {
				return ProcedureResult.ERROR;
			}

			if (!rriMapper0.apply((T) outputParticle).equals(rriParticle.getRri())) {
				return ProcedureResult.ERROR;
			}

			inputData.set(outputParticle);
			return ProcedureResult.POP_OUTPUT;
		} else {
			if (inputData.get() == null || !inputData.get().getClass().equals(particleClass0)) {
				return ProcedureResult.ERROR;
			}

			if (!outputParticle.getClass().equals(particleClass1)) {
				return ProcedureResult.ERROR;
			}

			if (!rriMapper1.apply((U) outputParticle).equals(rriParticle.getRri())) {
				return ProcedureResult.ERROR;
			}

			if (!combinedCheck.test((T) inputData.get(), (U) outputParticle)) {
				return ProcedureResult.ERROR;
			}
			return ProcedureResult.POP_INPUT_OUTPUT;
		}
	}

	@Override
	public boolean validateWitness(
		ProcedureResult result,
		Particle inputParticle,
		Particle outputParticle,
		AtomMetadata metadata
	) {
		RRIParticle rriParticle = (RRIParticle) inputParticle;
		switch (result) {
			case POP_OUTPUT:
				return true;
			case POP_INPUT_OUTPUT:
				return metadata.isSignedBy(rriParticle.getRri().getAddress());
			case POP_INPUT:
			default:
				throw new IllegalStateException();
		}
	}
}
