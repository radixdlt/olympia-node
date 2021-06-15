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

package com.radixdlt.atommodel.validators.state;

import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.crypto.ECPublicKey;

import java.util.Objects;

public final class PreparedValidatorUpdate implements Particle {
	public static final int RAKE_PERCENTAGE_GRANULARITY = 10 * 10; // 100 == 1.00%, 1 == 0.01%
	public static final int RAKE_MAX = 100 * RAKE_PERCENTAGE_GRANULARITY;
	public static final int RAKE_MIN = 0;

	private final long epoch;
	private final ECPublicKey validatorKey;
	private final int rakePercentage;

	public PreparedValidatorUpdate(long epoch, ECPublicKey validatorKey, int rakePercentage) {
		if (rakePercentage < RAKE_MIN || rakePercentage > RAKE_MAX) {
			throw new IllegalArgumentException("Illegal fee " + rakePercentage);
		}
		this.epoch = epoch;
		this.validatorKey = validatorKey;
		this.rakePercentage = rakePercentage;
	}

	public ECPublicKey getValidatorKey() {
		return validatorKey;
	}

	public long getEpoch() {
		return epoch;
	}

	public int getRakePercentage() {
		return rakePercentage;
	}

	@Override
	public int hashCode() {
		return Objects.hash(epoch, validatorKey, rakePercentage);
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof PreparedValidatorUpdate)) {
			return false;
		}

		var other = (PreparedValidatorUpdate) o;
		return this.epoch == other.epoch
			&& Objects.equals(this.validatorKey, other.validatorKey)
			&& this.rakePercentage == other.rakePercentage;
	}
}
