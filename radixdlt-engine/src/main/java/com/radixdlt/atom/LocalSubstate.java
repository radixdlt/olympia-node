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

package com.radixdlt.atom;

import com.radixdlt.constraintmachine.Particle;

import java.util.Objects;

/**
 * A particle which only has a local identifier since it's enclosing
 * transaction has not yet finished being constructed.
 */
public final class LocalSubstate {
	private final int index;
	private final Particle particle;

	private LocalSubstate(int index, Particle particle) {
		this.index = index;
		this.particle = particle;
	}

	public static LocalSubstate create(int index, Particle particle) {
		return new LocalSubstate(index, particle);
	}

	public Particle getParticle() {
		return particle;
	}

	public int getIndex() {
		return index;
	}

	@Override
	public int hashCode() {
		return Objects.hash(index, particle);
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof LocalSubstate)) {
			return false;
		}

		var other = (LocalSubstate) o;
		return this.index == other.index
			&& Objects.equals(this.particle, other.particle);
	}
}
