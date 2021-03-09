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

package com.radixdlt.middleware2.store;

import com.google.common.collect.ImmutableSet;
import com.radixdlt.crypto.Hasher;
import com.radixdlt.engine.RadixEngineAtom;
import com.radixdlt.identifiers.EUID;
import com.radixdlt.constraintmachine.CMMicroInstruction;
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.constraintmachine.Spin;
import com.radixdlt.store.StoreIndex;
import com.radixdlt.serialization.Serialization;
import com.radixdlt.serialization.SerializationUtils;
import com.radixdlt.utils.Longs;

import java.util.Set;

public class EngineAtomIndices {
	public enum IndexType {
		PARTICLE_UP((byte) 2),
		PARTICLE_DOWN((byte) 3),
		PARTICLE_CLASS((byte) 4),
		UID((byte) 5),
		DESTINATION((byte) 6);

		byte value;

		IndexType(byte value) {
			this.value = value;
		}

		public byte getValue() {
			return value;
		}
	}

	private final Set<StoreIndex> uniqueIndices;
	private final Set<StoreIndex> duplicateIndices;

	public EngineAtomIndices(Set<StoreIndex> uniqueIndices, Set<StoreIndex> duplicateIndices) {
		this.uniqueIndices = uniqueIndices;
		this.duplicateIndices = duplicateIndices;
	}

	public static EngineAtomIndices from(RadixEngineAtom radixEngineAtom, Serialization serialization, Hasher hasher) {
		ImmutableSet.Builder<StoreIndex> uniqueIndices = ImmutableSet.builder();
		ImmutableSet.Builder<StoreIndex> duplicateIndices = ImmutableSet.builder();

		radixEngineAtom.getCMInstruction().getMicroInstructions().stream()
				.filter(CMMicroInstruction::isPush)
				.forEach(i -> {
					Spin nextSpin = i.getNextSpin();
					final IndexType indexType;
					switch (nextSpin) {
						case UP:
							indexType = IndexType.PARTICLE_UP;
							break;
						case DOWN:
							indexType = IndexType.PARTICLE_DOWN;
							break;
						default:
							throw new IllegalStateException("Unknown SPIN state for particle " + nextSpin);
					}

					final byte[] indexableBytes = toByteArray(indexType, Particle.euidOf(i.getParticle(), hasher));
					uniqueIndices.add(new StoreIndex(indexableBytes));
				});


		final ImmutableSet<EUID> destinations = radixEngineAtom.getCMInstruction().getMicroInstructions().stream()
				.filter(CMMicroInstruction::isCheckSpin)
				.map(CMMicroInstruction::getParticle)
				.map(Particle::getDestinations)
				.flatMap(Set::stream)
				.collect(ImmutableSet.toImmutableSet());

		for (EUID euid : destinations) {
			duplicateIndices.add(new StoreIndex(toByteArray(IndexType.DESTINATION, euid)));
		}

		radixEngineAtom.getCMInstruction().getMicroInstructions().stream().filter(CMMicroInstruction::isCheckSpin)
				.forEach(checkSpin -> {
					// TODO: Remove
					// This does not handle nested particle classes.
					// If that ever becomes a problem, this is the place to fix it.
					// TODO Should probably not be using serialization for this
					final String idForClass = serialization.getIdForClass(checkSpin.getParticle().getClass());
					final EUID numericClassId = SerializationUtils.stringToNumericID(idForClass);
					duplicateIndices.add(new StoreIndex(
						IndexType.PARTICLE_CLASS.getValue(), toByteArray(IndexType.PARTICLE_CLASS, numericClassId)
					));
				});
		return new EngineAtomIndices(uniqueIndices.build(), duplicateIndices.build());
	}

	public Set<StoreIndex> getUniqueIndices() {
		return uniqueIndices;
	}

	public Set<StoreIndex> getDuplicateIndices() {
		return duplicateIndices;
	}

	public static byte[] toByteArray(IndexType type, EUID id) {
		if (id == null) {
			throw new IllegalArgumentException("EUID is null");
		}

		byte[] idBytes = id.toByteArray();
		byte[] typeBytes = new byte[idBytes.length + 1];
		typeBytes[0] = type.value;
		System.arraycopy(idBytes, 0, typeBytes, 1, idBytes.length);
		return typeBytes;
	}

	public static byte[] toByteArray(byte prefix, byte[] idBytes) {
		if (idBytes == null) {
			throw new IllegalArgumentException("EUID is null");
		}

		byte[] typeBytes = new byte[idBytes.length + 1];
		typeBytes[0] = prefix;
		System.arraycopy(idBytes, 0, typeBytes, 1, idBytes.length);
		return typeBytes;
	}

	public static byte[] toByteArray(byte prefix, long value) {
		byte[] typeBytes = new byte[Long.BYTES + 1];
		typeBytes[0] = prefix;
		System.arraycopy(Longs.toByteArray(value), 0, typeBytes, 1, Long.BYTES);
		return typeBytes;
	}

	public static EUID toEUID(byte[] bytes) {
		byte[] temp = new byte[bytes.length - 1];
		System.arraycopy(bytes, 1, temp, 0, temp.length);
		return new EUID(temp);
	}

}
