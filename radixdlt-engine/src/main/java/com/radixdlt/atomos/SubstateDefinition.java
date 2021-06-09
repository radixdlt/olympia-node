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
import com.radixdlt.constraintmachine.StatelessSubstateVerifier;

import java.util.function.Predicate;

/**
 * Defines how to retrieve important properties from a given particle type.
 * @param <T> the particle class
 */
// FIXME: unchecked, rawtypes
@SuppressWarnings({"unchecked", "rawtypes"})
public final class SubstateDefinition<T extends Particle> {
	private final Class<T> substateClass;
	private final StatelessSubstateVerifier<T> staticValidation; // may be null
	private final Predicate<T> virtualized; // may be null

	public SubstateDefinition(
		Class<T> substateClass,
		StatelessSubstateVerifier<T> staticValidation
	) {
		this.substateClass = substateClass;
		this.staticValidation = staticValidation;
		this.virtualized = x -> false;
	}

	public SubstateDefinition(
		Class<T> substateClass,
		StatelessSubstateVerifier<T> staticValidation,
		Predicate<T> virtualized
	) {
		this.substateClass = substateClass;
		this.staticValidation = staticValidation;
		this.virtualized = virtualized;
	}

	public Class<T> getSubstateClass() {
		return substateClass;
	}

	public StatelessSubstateVerifier<Particle> getStaticValidation() {
		return p -> staticValidation.verify((T) p);
	}

	public Predicate<T> getVirtualized() {
		return virtualized;
	}
}
