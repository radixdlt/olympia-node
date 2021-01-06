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

package com.radixdlt.constraintmachine;

import com.google.common.base.Suppliers;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * A pointer into an atom
 * TODO: cache objects as they are used often
 */
public final class DataPointer {
	private final long particleGroupIndex;
	private final long particleIndex;
	private final Supplier<String> pointerToIssue;

	DataPointer(long particleGroupIndex, long particleIndex) {
		if (particleGroupIndex < -1) {
			throw new IllegalArgumentException("Particle group index must be >= -1.");
		}

		if (particleIndex < -1) {
			throw new IllegalArgumentException("Particle index must be >= -1.");
		}

		if (particleGroupIndex < 0) {
			if (particleIndex >= 0) {
				throw new IllegalArgumentException("Particle index must be included with a valid particle group index");
			}
		}

		this.particleGroupIndex = particleGroupIndex;
		this.particleIndex = particleIndex;
		this.pointerToIssue = Suppliers.memoize(() -> {
			StringBuilder stringBuilder = new StringBuilder("#");

			if (particleGroupIndex >= 0) {
				stringBuilder.append("/particleGroups/");
				stringBuilder.append(particleGroupIndex);
			}

			if (particleIndex >= 0) {
				stringBuilder.append("/particles/");
				stringBuilder.append(particleIndex);
			}

			return stringBuilder.toString();
		});
	}

	public static DataPointer ofParticleGroup(long particleGroupIndex) {
		return new DataPointer(particleGroupIndex, -1);
	}

	public static DataPointer ofParticle(long particleGroupIndex, long particleIndex) {
		return new DataPointer(particleGroupIndex, particleIndex);
	}

	public static DataPointer ofAtom() {
		return new DataPointer(-1, -1);
	}

	public long getParticleGroupIndex() {
		return particleGroupIndex;
	}

	public long getParticleIndex() {
		return particleIndex;
	}

	@Override
	public String toString() {
		return pointerToIssue.get();
	}

	@Override
	public int hashCode() {
		return Objects.hash(particleGroupIndex, particleIndex);
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof DataPointer)) {
			return false;
		}
		DataPointer p = (DataPointer) o;
		return p.particleIndex == particleIndex && p.particleGroupIndex == particleGroupIndex;
	}
}
