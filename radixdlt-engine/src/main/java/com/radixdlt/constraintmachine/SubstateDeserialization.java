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
import com.radixdlt.serialization.DeserializeException;
import com.radixdlt.utils.Pair;

import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

public final class SubstateDeserialization {
	private final Map<Byte, SubstateDefinition<? extends Particle>> byteToDeserializer;

	public SubstateDeserialization(
		Collection<SubstateDefinition<? extends Particle>> definitions
	) {
		this.byteToDeserializer = definitions.stream()
			.flatMap(d -> d.getTypeBytes().stream().map(b -> Pair.of(b, d)))
			.collect(Collectors.toMap(Pair::getFirst, Pair::getSecond));
	}

	Class<? extends Particle> byteToClass(Byte typeByte) throws DeserializeException {
		var definition = byteToDeserializer.get(typeByte);
		if (definition == null) {
			throw new DeserializeException("Unknown byte type: " + typeByte);
		}
		return definition.getSubstateClass();
	}

	Particle deserialize(ByteBuffer buf) throws DeserializeException {
		var typeByte = buf.get();
		var deserializer = byteToDeserializer.get(typeByte);
		if (deserializer == null) {
			throw new DeserializeException("Unknown byte type: " + typeByte);
		}

		return deserializer.getDeserializer().deserialize(typeByte, buf);
	}
}
