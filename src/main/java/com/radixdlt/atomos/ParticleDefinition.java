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
	private final Function<T, Spin> virtualizeSpin;
	private final boolean allowsTransitionsFromOutsideScrypts;

	// TODO convert to builder to simplify API/reduce number of method variations
	ParticleDefinition(
		Function<T, Stream<RadixAddress>> addressMapper,
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

	Function<T, Stream<RadixAddress>> getAddressMapper() {
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
}
