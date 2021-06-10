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
import com.radixdlt.constraintmachine.SubstateDeserializer;

import java.util.Set;
import java.util.function.Predicate;

/**
 * Defines how to retrieve important properties from a given particle type.
 * @param <T> the particle class
 */
// FIXME: unchecked, rawtypes
@SuppressWarnings({"unchecked", "rawtypes"})
public final class SubstateDefinition<T extends Particle> {
	private final Class<T> substateClass;
	private final Set<Byte> typeBytes;
	private final SubstateDeserializer<T> deserializer;
	private final Predicate<T> virtualized; // may be null

	public SubstateDefinition(
		Class<T> substateClass,
		Set<Byte> typeBytes,
		SubstateDeserializer<T> deserializer
	) {
		this.substateClass = substateClass;
		this.typeBytes = typeBytes;
		this.deserializer = deserializer;
		this.virtualized = s -> false;
	}

	public SubstateDefinition(
		Class<T> substateClass,
		Set<Byte> typeBytes,
		SubstateDeserializer<T> deserializer,
		Predicate<T> virtualized
	) {
		this.substateClass = substateClass;
		this.typeBytes = typeBytes;
		this.deserializer = deserializer;
		this.virtualized = virtualized;
	}

	public Set<Byte> getTypeBytes() {
		return typeBytes;
	}

	public Class<T> getSubstateClass() {
		return substateClass;
	}

	public SubstateDeserializer<T> getDeserializer() {
		return deserializer;
	}

	public Predicate<T> getVirtualized() {
		return virtualized;
	}
}
