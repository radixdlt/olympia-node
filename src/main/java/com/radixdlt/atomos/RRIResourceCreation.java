package com.radixdlt.atomos;

import com.google.common.collect.ImmutableSet;
import com.radixdlt.atoms.Particle;
import com.radixdlt.common.Pair;
import com.radixdlt.constraintmachine.AtomMetadata;
import com.radixdlt.constraintmachine.TransitionProcedure;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

public class RRIResourceCreation<T extends Particle> implements TransitionProcedure {
	private final Class<T> particleClass;
	private final Function<T, RRI> rriMapper;

	public RRIResourceCreation(Class<T> particleClass, Function<T, RRI> rriMapper) {
		this.particleClass = particleClass;
		this.rriMapper = rriMapper;
	}

	@Override
	public ImmutableSet<Pair<Class<? extends Particle>, Class<? extends Particle>>> supports() {
		return ImmutableSet.of(Pair.of(RRIParticle.class, particleClass));
	}


	@Override
	public ProcedureResult execute(
		Particle inputParticle,
		AtomicReference<Object> inputData,
		Particle outputParticle,
		AtomicReference<Object> outputData
	) {
		RRIParticle rriParticle = (RRIParticle) inputParticle;

		if (!rriMapper.apply((T) outputParticle).equals(rriParticle.getRri())) {
			return ProcedureResult.ERROR;
		}

		return ProcedureResult.POP_INPUT_OUTPUT;
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
