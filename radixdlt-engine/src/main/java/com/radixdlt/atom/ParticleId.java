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

import com.google.common.hash.HashCode;
import com.radixdlt.DefaultSerialization;
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.crypto.HashUtils;
import com.radixdlt.serialization.DsonOutput;
import org.bouncycastle.util.encoders.Hex;

import java.util.Objects;

public final class ParticleId {
	private final HashCode particleId;
	private ParticleId(HashCode particleId) {
		this.particleId = Objects.requireNonNull(particleId);
	}

	public static ParticleId of(Particle particle) {
		var dson = DefaultSerialization.getInstance().toDson(particle, DsonOutput.Output.ALL);
		var particleHash = HashUtils.sha256(dson);
		return new ParticleId(particleHash);
	}

	public static ParticleId ofVirtualParticle(Particle particle) {
		var dson = DefaultSerialization.getInstance().toDson(particle, DsonOutput.Output.ALL);
		var particleHash = HashUtils.sha256(dson);
		return new ParticleId(particleHash);
	}

	public static ParticleId fromBytes(byte[] bytes) {
		return new ParticleId(HashCode.fromBytes(bytes));
	}

	public byte[] asBytes() {
		return particleId.asBytes();
	}

	@Override
	public String toString() {
		return Hex.toHexString(particleId.asBytes());
	}

	@Override
	public int hashCode() {
		return Objects.hash(particleId);
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof ParticleId)) {
			return false;
		}

		var other = (ParticleId) o;

		return Objects.equals(this.particleId, other.particleId);
	}
}
