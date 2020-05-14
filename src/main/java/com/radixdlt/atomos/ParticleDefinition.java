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
import com.radixdlt.constraintmachine.Spin;
import com.radixdlt.identifiers.RRI;
import com.radixdlt.identifiers.RadixAddress;

import java.util.Collections;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Defines how to retrieve important properties from a given particle type.
 * @param <T> the particle class
 */
public class ParticleDefinition<T extends Particle> {
	private final Function<T, Set<RadixAddress>> addressMapper;
	private final Function<T, Result> staticValidation;
	private final Function<T, RRI> rriMapper;
	private final Function<T, Spin> virtualizeSpin;
	private final boolean allowsTransitionsFromOutsideScrypts;

	// TODO convert to builder to simplify API/reduce number of method variations
	ParticleDefinition(
		Function<T, Set<RadixAddress>> addressMapper,
		Function<T, Result> staticValidation,
		Function<T, RRI> rriMapper,
		Function<T, Spin> virtualizeSpin,
		boolean allowsTransitionsFromOutsideScrypts
	) {
		this.staticValidation = staticValidation;
		this.addressMapper = addressMapper;
		this.rriMapper = rriMapper;
		this.virtualizeSpin = virtualizeSpin;
		this.allowsTransitionsFromOutsideScrypts = allowsTransitionsFromOutsideScrypts;
	}

	Function<T, Set<RadixAddress>> getAddressMapper() {
		return addressMapper;
	}

	Function<T, Result> getStaticValidation() {
		return staticValidation;
	}

	Function<T, RRI> getRriMapper() {
		return rriMapper;
	}

	Function<T, Spin> getVirtualizeSpin() {
		return virtualizeSpin;
	}

	public boolean allowsTransitionsFromOutsideScrypts() {
		return allowsTransitionsFromOutsideScrypts;
	}

	public static <T extends Particle> Builder<T> builder() {
		return new Builder<>();
	}

	public static class Builder<T extends Particle> {
		private Function<T, Set<RadixAddress>> addressMapper;
		private Function<T, Result> staticValidation = x -> Result.success();
		private Function<T, RRI> rriMapper;
		private Function<T, Spin> virtualizeSpin;
		private boolean allowsTransitionsFromOutsideScrypts;

		private Builder() {
		}

		public Builder<T> singleAddressMapper(Function<T, RadixAddress> addressMapper) {
			this.addressMapper = p -> Collections.singleton(addressMapper.apply(p));
			return this;
		}

		public Builder<T> addressMapper(Function<T, Set<RadixAddress>> addressMapper) {
			this.addressMapper = addressMapper;
			return this;
		}

		public Builder<T> staticValidation(Function<T, Result> staticValidation) {
			this.staticValidation = staticValidation;
			return this;
		}

		public Builder<T> rriMapper(Function<T, RRI> rriMapper) {
			this.rriMapper = rriMapper;
			return this;
		}

		public Builder<T> virtualizeSpin(Function<T, Spin> virtualizeSpin) {
			this.virtualizeSpin = virtualizeSpin;
			return this;
		}

		public Builder<T> allowTransitionsFromOutsideScrypts() {
			this.allowsTransitionsFromOutsideScrypts = true;
			return this;
		}

		public <U extends Particle> ParticleDefinition<U> build() {
			if (addressMapper == null) {
				throw new IllegalStateException("addressMapper is required");
			}

			// cast as necessary
			return new ParticleDefinition<>(
				addressMapper == null ? null : p -> addressMapper.apply((T) p),
				staticValidation == null ? null : p -> staticValidation.apply((T) p),
				rriMapper == null ? null : p -> rriMapper.apply((T) p),
				virtualizeSpin == null ? null : p -> virtualizeSpin.apply((T) p),
				allowsTransitionsFromOutsideScrypts
			);
		}
	}
}
