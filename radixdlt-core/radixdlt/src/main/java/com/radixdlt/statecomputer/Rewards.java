/*
 * (C) Copyright 2021 Radix DLT Ltd
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

package com.radixdlt.statecomputer;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.utils.UInt256;

import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Wrapper class for amount staked per node
 */
public final class Rewards {
	private final ImmutableMap<ECPublicKey, UInt256> stakedAmounts;

	private Rewards(ImmutableMap<ECPublicKey, UInt256> stakedAmounts) {
		this.stakedAmounts = stakedAmounts;
	}

	public static Rewards create() {
		return new Rewards(ImmutableMap.of());
	}

	public ImmutableMap<ECPublicKey, UInt256> toMap() {
		return stakedAmounts;
	}

	public Rewards add(ECPublicKey delegatedKey, UInt256 amount) {
		if (amount.isZero()) {
			return this;
		}

		final var nextAmount = this.stakedAmounts.getOrDefault(delegatedKey, UInt256.ZERO).add(amount);
		final var nextStakedAmounts = Stream.concat(
			Stream.of(Maps.immutableEntry(delegatedKey, nextAmount)),
			this.stakedAmounts.entrySet().stream().filter(e -> !delegatedKey.equals(e.getKey()))
		).collect(ImmutableMap.toImmutableMap(Map.Entry::getKey, Map.Entry::getValue));

		return new Rewards(nextStakedAmounts);
	}

	public Rewards remove(ECPublicKey delegatedKey, UInt256 amount) {
		if (!this.stakedAmounts.containsKey(delegatedKey)) {
			throw new IllegalStateException("Removing stake which doesn't exist.");
		}

		if (amount.isZero()) {
			return this;
		}

		final var oldAmount = this.stakedAmounts.get(delegatedKey);
		final var comparison = amount.compareTo(oldAmount);

		if (comparison == 0) {
			// remove stake
			final var nextStakedAmounts = this.stakedAmounts.entrySet().stream()
				.filter(e -> !delegatedKey.equals(e.getKey()))
				.collect(ImmutableMap.toImmutableMap(Map.Entry::getKey, Map.Entry::getValue));
			return new Rewards(nextStakedAmounts);
		} else if (comparison < 0) {
			// reduce stake
			final var nextAmount = oldAmount.subtract(amount);
			final var nextStakedAmounts = Stream.concat(
				Stream.of(Maps.immutableEntry(delegatedKey, nextAmount)),
				this.stakedAmounts.entrySet().stream().filter(e -> !delegatedKey.equals(e.getKey()))
			).collect(ImmutableMap.toImmutableMap(Map.Entry::getKey, Map.Entry::getValue));
			return new Rewards(nextStakedAmounts);
		} else {
			throw new IllegalStateException("Removing stake which doesn't exist.");
		}
	}

	@Override
	public String toString() {
		return this.stakedAmounts.toString();
	}

	@Override
	public int hashCode() {
		return Objects.hash(stakedAmounts);
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof Rewards)) {
			return false;
		}

		var other = (Rewards) o;
		return Objects.equals(this.stakedAmounts, other.stakedAmounts);
	}
}
