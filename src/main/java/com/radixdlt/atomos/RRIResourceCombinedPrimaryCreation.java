package com.radixdlt.atomos;

import com.radixdlt.atoms.Particle;
import com.radixdlt.common.Pair;
import com.radixdlt.constraintmachine.AtomMetadata;
import com.radixdlt.constraintmachine.TransitionProcedure;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

public class RRIResourceCombinedPrimaryCreation<T extends Particle, U extends Particle> implements TransitionProcedure {
	private final Class<T> particleClass0;
	private final Function<T, RRI> rriMapper0;

	public RRIResourceCombinedPrimaryCreation(
		Class<T> particleClass0,
		Function<T, RRI> rriMapper0
	) {
		this.particleClass0 = particleClass0;
		this.rriMapper0 = rriMapper0;
	}

	@Override
	public Pair<Class<? extends Particle>, Class<? extends Particle>> supports() {
		return Pair.of(RRIParticle.class, particleClass0);
	}

	@Override
	public ProcedureResult execute(
		Particle inputParticle,
		AtomicReference<Object> inputData,
		Particle outputParticle,
		AtomicReference<Object> outputData
	) {
		RRIParticle rriParticle = (RRIParticle) inputParticle;

		if (!outputParticle.getClass().equals(particleClass0)) {
			return ProcedureResult.ERROR;
		}

		if (!rriMapper0.apply((T) outputParticle).equals(rriParticle.getRri())) {
			return ProcedureResult.ERROR;
		}

		inputData.set(outputParticle);
		return ProcedureResult.POP_OUTPUT;
	}

	@Override
	public boolean validateWitness(
		ProcedureResult result,
		Particle inputParticle,
		Particle outputParticle,
		AtomMetadata metadata
	) {
		switch (result) {
			case POP_OUTPUT:
				return true;
			case POP_INPUT_OUTPUT:
			case POP_INPUT:
			default:
				throw new IllegalStateException();
		}
	}
}
