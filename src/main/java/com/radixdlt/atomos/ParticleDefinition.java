package com.radixdlt.atomos;

import com.radixdlt.atoms.Particle;
import java.util.function.Function;
import java.util.stream.Stream;

class ParticleDefinition<T extends Particle> {
	private final Function<T, Stream<RadixAddress>> addressMapper;
	private final Function<T, Result> staticValidation;
	private final Function<T, RRI> rriMapper;

	ParticleDefinition(
		Function<T, Stream<RadixAddress>> addressMapper,
		Function<T, Result> staticValidation,
		Function<T, RRI> rriMapper
	) {
		this.staticValidation = staticValidation;
		this.addressMapper = addressMapper;
		this.rriMapper = rriMapper;
	}

	Function<T, Stream<RadixAddress>> getAddressMapper() {
		return addressMapper;
	}

	Function<T, Result> getStaticValidation() {
		return staticValidation;
	}

	Function<T, RRI> getRriMapper() {
		return rriMapper;
	}
}
