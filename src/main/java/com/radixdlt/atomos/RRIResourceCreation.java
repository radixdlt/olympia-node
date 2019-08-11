package com.radixdlt.atomos;

import com.radixdlt.atoms.Particle;
import com.radixdlt.constraintmachine.AtomMetadata;
import com.radixdlt.constraintmachine.TransitionProcedure;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

public class RRIResourceCreation<T extends Particle> implements TransitionProcedure<RRIParticle, T> {
	private final Class<T> particleClass;
	private final Function<T, RRI> rriMapper;

	public RRIResourceCreation(Class<T> particleClass, Function<T, RRI> rriMapper) {
		this.particleClass = particleClass;
		this.rriMapper = rriMapper;
	}

	@Override
	public ProcedureResult execute(
		RRIParticle inputParticle,
		AtomicReference<Object> inputData,
		T outputParticle,
		AtomicReference<Object> outputData
	) {
		if (!rriMapper.apply(outputParticle).equals(inputParticle.getRri())) {
			return ProcedureResult.ERROR;
		}

		return ProcedureResult.POP_INPUT_OUTPUT;
	}

	@Override
	public boolean validateWitness(
		ProcedureResult result,
		RRIParticle inputParticle,
		T outputParticle,
		AtomMetadata metadata
	) {
		switch (result) {
			case POP_OUTPUT:
				return true;
			case POP_INPUT_OUTPUT:
				return metadata.isSignedBy(inputParticle.getRri().getAddress());
			case POP_INPUT:
			default:
				throw new IllegalStateException();
		}
	}
}
