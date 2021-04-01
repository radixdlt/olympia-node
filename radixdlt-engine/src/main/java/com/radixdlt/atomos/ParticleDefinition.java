/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 */

package com.radixdlt.atomos;

import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.identifiers.RRI;

import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Defines how to retrieve important properties from a given particle type.
 * @param <T> the particle class
 */
// FIXME: unchecked, rawtypes
@SuppressWarnings({"unchecked", "rawtypes"})
public class ParticleDefinition<T extends Particle> {
	private final Function<T, Result> staticValidation; // may be null
	private final Function<T, RRI> rriMapper; // may be null
	private final Predicate<T> virtualizeSpin; // may be null
	private final boolean allowsTransitionsFromOutsideScrypts;

	private ParticleDefinition(
		Function<T, Result> staticValidation,
		Function<T, RRI> rriMapper,
		Predicate<T> virtualizeSpin,
		boolean allowsTransitionsFromOutsideScrypts
	) {
		this.staticValidation = staticValidation;
		this.rriMapper = rriMapper;
		this.virtualizeSpin = virtualizeSpin;
		this.allowsTransitionsFromOutsideScrypts = allowsTransitionsFromOutsideScrypts;
	}

	Function<T, Result> getStaticValidation() {
		return staticValidation;
	}

	Function<T, RRI> getRriMapper() {
		return rriMapper;
	}

	Predicate<T> getVirtualizeSpin() {
		return virtualizeSpin;
	}

	public boolean allowsTransitionsFromOutsideScrypts() {
		return allowsTransitionsFromOutsideScrypts;
	}

	/**
	 * Creates a Builder for {@link ParticleDefinition}s.
	 * @param <T> The type of the {@link ParticleDefinition}
	 * @return The builder
	 */
	public static <T extends Particle> Builder<T> builder() {
		return new Builder<>();
	}

	/**
	 * A Builder for {@link ParticleDefinition}s
	 * @param <T> The type of the particle to be defined
	 */
	public static class Builder<T extends Particle> {
		private Function<T, Result> staticValidation = x -> Result.success();
		private Function<T, RRI> rriMapper;
		private Predicate<T> virtualizedParticles = x -> false;
		private boolean allowsTransitionsFromOutsideScrypts = false;

		private Builder() {
		}

		public Builder<T> staticValidation(Function<T, Result> staticValidation) {
			this.staticValidation = staticValidation;
			return this;
		}

		public Builder<T> rriMapper(Function<T, RRI> rriMapper) {
			this.rriMapper = rriMapper;
			return this;
		}

		public Builder<T> virtualizeUp(Predicate<T> virtualizeParticles) {
			this.virtualizedParticles = virtualizeParticles;
			return this;
		}

		public Builder<T> allowTransitionsFromOutsideScrypts() {
			this.allowsTransitionsFromOutsideScrypts = true;
			return this;
		}

		/**
		 * Builds the {@link ParticleDefinition} with the given properties, casting as necessary.
		 * All properties except the address mapper are optional.
		 *
		 * TODO Cleanup: Recasting the Particle type here is non-ideal, but fixing it would take too long.
		 * @param <U> The type of the built ParticleDefinition (must be castable from builder type T)
		 * @return The built {@link ParticleDefinition}
		 */
		public <U extends Particle> ParticleDefinition<U> build() {
			// cast as necessary
			return new ParticleDefinition<>(
				staticValidation == null ? null : p -> staticValidation.apply((T) p),
				rriMapper == null ? null : p -> rriMapper.apply((T) p),
				p -> virtualizedParticles.test((T) p),
				allowsTransitionsFromOutsideScrypts
			);
		}
	}
}
