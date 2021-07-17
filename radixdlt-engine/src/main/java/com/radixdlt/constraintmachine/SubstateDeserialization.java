/*
 * (C) Copyright 2021 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 */

package com.radixdlt.constraintmachine;

import com.radixdlt.atomos.SubstateDefinition;
import com.radixdlt.engine.parser.exceptions.SubstateDeserializationException;
import com.radixdlt.serialization.DeserializeException;

import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

public final class SubstateDeserialization {
	private final Map<Byte, SubstateDefinition<? extends Particle>> byteToDeserializer;
	private final Map<Class<? extends Particle>, Byte> classToTypeByte;

	public SubstateDeserialization(
		Collection<SubstateDefinition<? extends Particle>> definitions
	) {
		this.byteToDeserializer = definitions.stream()
			.collect(Collectors.toMap(SubstateDefinition::getTypeByte, d -> d));
		this.classToTypeByte = definitions.stream()
			.collect(Collectors.toMap(SubstateDefinition::getSubstateClass, SubstateDefinition::getTypeByte));
	}

	public Class<? extends Particle> byteToClass(Byte typeByte) throws DeserializeException {
		var definition = byteToDeserializer.get(typeByte);
		if (definition == null) {
			throw new DeserializeException("Unknown substate byte type: " + typeByte);
		}
		return definition.getSubstateClass();
	}

	public byte classToByte(Class<? extends Particle> substateClass) {
		var b = classToTypeByte.get(substateClass);
		if (b == null) {
			throw new IllegalStateException("Unknown substateClass: " + substateClass);
		}
		return b;
	}

	public <T extends Particle> SubstateIndex<T> index(Class<T> substateClass) {
		return SubstateIndex.create(classToByte(substateClass), substateClass);
	}

	public Particle deserialize(byte[] b) throws DeserializeException {
		return deserialize(ByteBuffer.wrap(b));
	}

	public Particle deserialize(ByteBuffer buf) throws DeserializeException {
		var typeByte = buf.get();
		var deserializer = byteToDeserializer.get(typeByte);
		if (deserializer == null) {
			throw new DeserializeException("Unknown byte type: " + typeByte);
		}

		try {
			return deserializer.getDeserializer().deserialize(buf);
		} catch (Exception e) {
			throw new SubstateDeserializationException(deserializer.getSubstateClass(), e);
		}
	}
}
