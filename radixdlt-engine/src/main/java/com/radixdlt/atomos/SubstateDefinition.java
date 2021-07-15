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

import com.radixdlt.constraintmachine.KeyDeserializer;
import com.radixdlt.constraintmachine.KeySerializer;
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.constraintmachine.SubstateDeserializer;
import com.radixdlt.constraintmachine.SubstateSerializer;
import com.radixdlt.constraintmachine.VirtualMapper;
import com.radixdlt.serialization.DeserializeException;

/**
 * Defines how to retrieve important properties from a given particle type.
 * @param <T> the particle class
 */
// FIXME: unchecked, rawtypes
@SuppressWarnings({"unchecked", "rawtypes"})
public final class SubstateDefinition<T extends Particle> {
	private final Class<T> substateClass;
	private final byte typeByte;
	private final SubstateDeserializer<T> deserializer;
	private final SubstateSerializer<T> serializer;

	private final KeyDeserializer keyDeserializer;
	private final KeySerializer keySerializer;
	private final VirtualMapper virtualSerializer;

	public SubstateDefinition(
		Class<T> substateClass,
		byte typeByte,
		SubstateDeserializer<T> deserializer,
		SubstateSerializer<T> serializer
	) {
		this.substateClass = substateClass;
		this.typeByte = typeByte;
		this.deserializer = deserializer;
		this.serializer = serializer;
		this.keyDeserializer = buf -> {
			throw new DeserializeException("Virtual substate not supported");
		};
		this.keySerializer = (k, buf) -> {
			throw new IllegalStateException("Cannot create key");
		};
		this.virtualSerializer = o -> {
			throw new IllegalStateException("Cannot virtualize");
		};
	}

	public SubstateDefinition(
		Class<T> substateClass,
		byte typeByte,
		SubstateDeserializer<T> deserializer,
		SubstateSerializer<T> serializer,
		KeySerializer keySerializer
	) {
		this.substateClass = substateClass;
		this.typeByte = typeByte;
		this.deserializer = deserializer;
		this.serializer = serializer;

		this.keySerializer = keySerializer;
		this.keyDeserializer = buf -> {
			throw new DeserializeException("Virtual substate not supported");
		};
		this.virtualSerializer = o -> {
			throw new IllegalStateException("Cannot virtualize");
		};
	}

	public SubstateDefinition(
		Class<T> substateClass,
		byte typeByte,
		SubstateDeserializer<T> deserializer,
		SubstateSerializer<T> serializer,
		KeyDeserializer keyDeserializer,
		KeySerializer keySerializer,
		VirtualMapper virtualMapper
	) {
		this.substateClass = substateClass;
		this.typeByte = typeByte;
		this.deserializer = deserializer;
		this.serializer = serializer;

		this.keyDeserializer = keyDeserializer;
		this.keySerializer = keySerializer;
		this.virtualSerializer = virtualMapper;
	}

	public byte getTypeByte() {
		return typeByte;
	}

	public Class<T> getSubstateClass() {
		return substateClass;
	}

	public SubstateSerializer<T> getSerializer() {
		return serializer;
	}

	public SubstateDeserializer<T> getDeserializer() {
		return deserializer;
	}

	public KeySerializer getKeySerializer() {
		return keySerializer;
	}

	public KeyDeserializer getKeyDeserializer() {
		return keyDeserializer;
	}

	public VirtualMapper getVirtualMapper() {
		return virtualSerializer;
	}
}
