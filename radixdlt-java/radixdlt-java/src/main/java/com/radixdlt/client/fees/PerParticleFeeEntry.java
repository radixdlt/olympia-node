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

package com.radixdlt.client.fees;

import java.util.Objects;
import java.util.Set;

import com.radixdlt.atom.AtomBuilder;
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.utils.UInt256;
import com.radixdlt.utils.UInt384;

/**
 * Fee entry for a per-particle fee.
 */
public final class PerParticleFeeEntry implements FeeEntry {
	private final long threshold;
	private final Class<? extends Particle> particleType;
	private final UInt256 fee;

	private PerParticleFeeEntry(long threshold, Class<? extends Particle> particleType, UInt256 fee) {
		if (threshold < 0) {
			throw new IllegalArgumentException("Threshold must be non-negative: " + threshold);
		}
		this.threshold = threshold;
		this.particleType = Objects.requireNonNull(particleType);
		this.fee = Objects.requireNonNull(fee);
	}

	public static PerParticleFeeEntry of(Class<? extends Particle> particleType, long threshold, UInt256 fee) {
		return new PerParticleFeeEntry(threshold, particleType, fee);
	}

	public long threshold() {
		return this.threshold;
	}

	public Class<? extends Particle> particleType() {
		return this.particleType;
	}

	public UInt256 fee() {
		return this.fee;
	}

	@Override
	public UInt256 feeFor(AtomBuilder a, int feeSize, Set<Particle> outputs) {
		long particleCount = outputs.stream()
			.filter(this.particleType::isInstance)
			.count();
		if (particleCount <= this.threshold) {
			return UInt256.ZERO;
		}
		long overThresholdParticles = particleCount - this.threshold;
		UInt384 totalFee = UInt384.from(this.fee).multiply(UInt256.from(overThresholdParticles));
		if (!totalFee.getHigh().isZero()) {
			throw new ArithmeticException("Fee overflow");
		}
		return totalFee.getLow();
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.threshold, this.particleType, this.fee);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (!(obj instanceof PerParticleFeeEntry)) {
			return false;
		}
		PerParticleFeeEntry that = (PerParticleFeeEntry) obj;
		return this.threshold == that.threshold
			&& Objects.equals(this.particleType, that.particleType)
			&& Objects.equals(this.fee, that.fee);
	}

	@Override
	public String toString() {
		return String.format("%s[>%s %s, %s per particle]",
			getClass().getSimpleName(), this.threshold, this.particleType.getSimpleName(), this.fee);
	}
}
