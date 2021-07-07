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

import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

public final class SubstateSerialization {
	private final Map<Class<? extends Particle>, SubstateSerializer<Particle>> classToSerializer;
	private final Map<Class<? extends Particle>, VirtualSubstateSerializer> classToVirtualSerializer;
	private final Map<Class<? extends Particle>, Byte> classToTypeByte;


	public SubstateSerialization(
		Collection<SubstateDefinition<? extends Particle>> definitions
	) {
		this.classToTypeByte = definitions.stream()
			.collect(Collectors.toMap(SubstateDefinition::getSubstateClass, SubstateDefinition::getTypeByte));
		this.classToSerializer = definitions.stream()
			.collect(Collectors.toMap(
				SubstateDefinition::getSubstateClass,
				d -> (s, buf) -> ((SubstateSerializer<Particle>) d.getSerializer()).serialize(s, buf)
			));
		this.classToVirtualSerializer = definitions.stream()
			.collect(Collectors.toMap(
				SubstateDefinition::getSubstateClass,
				SubstateDefinition::getVirtualSerializer
			));
	}

	public byte[] serialize(Particle p) {
		var serializer = classToSerializer.get(p.getClass());
		if (serializer == null) {
			throw new IllegalStateException("No serializer for particle: " + p);
		}

		// TODO: Remove buf allocation
		var buf = ByteBuffer.allocate(1024);
		buf.put(classToTypeByte.get(p.getClass()));
		serializer.serialize(p, buf);
		var position = buf.position();
		buf.rewind();
		var bytes = new byte[position];
		buf.get(bytes);
		return bytes;
	}

	public void serialize(Particle p, ByteBuffer buffer) {
		var serializer = classToSerializer.get(p.getClass());
		serializer.serialize(p, buffer);
	}

	public byte[] serializeVirtual(Class<? extends Particle> substateClass, Object key) {
		var serializer = classToVirtualSerializer.get(substateClass);
		// TODO: Remove buf allocation
		var buf = ByteBuffer.allocate(1024);
		buf.put(classToTypeByte.get(substateClass));
		serializer.serialize(key, buf);
		var position = buf.position();
		buf.rewind();
		var bytes = new byte[position];
		buf.get(bytes);
		return bytes;
	}
}
