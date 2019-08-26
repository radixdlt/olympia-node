package com.radixdlt.atomos;

import com.radixdlt.atoms.Particle;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Defines how to retrieve important properties from a given particle type.
 * @param <T> the particle class
 */
class ParticleDefinition<T extends Particle> {
	private final Function<T, Stream<RadixAddress>> addressMapper;
	private final Function<T, Result> staticValidation;
	private final Function<T, RRI> rriMapper;
	private final boolean allowsTransitionsFromOutsideScrypts;

	ParticleDefinition(
		Function<T, Stream<RadixAddress>> addressMapper,
		Function<T, Result> staticValidation,
		Function<T, RRI> rriMapper,
		boolean allowsTransitionsFromOutsideScrypts
	) {
		this.staticValidation = staticValidation;
		this.addressMapper = addressMapper;
		this.rriMapper = rriMapper;
		this.allowsTransitionsFromOutsideScrypts = allowsTransitionsFromOutsideScrypts;
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

	public boolean allowsTransitionsFromOutsideScrypts() {
		return allowsTransitionsFromOutsideScrypts;
	}
}
