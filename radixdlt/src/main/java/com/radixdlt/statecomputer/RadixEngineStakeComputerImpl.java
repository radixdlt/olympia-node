/*
 * (C) Copyright 2020 Radix DLT Ltd
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
 */

package com.radixdlt.statecomputer;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.identifiers.RRI;
import com.radixdlt.identifiers.RadixAddress;
import com.radixdlt.utils.UInt256;

import java.util.Objects;
import java.util.Map;
import java.util.stream.Stream;

import javax.annotation.concurrent.NotThreadSafe;

/**
 * Helper class to maintain staked amounts from the radix engine state.
 */
@NotThreadSafe
public final class RadixEngineStakeComputerImpl implements RadixEngineStakeComputer {
	private final RRI stakingToken;
	private final ImmutableMap<ECPublicKey, UInt256> stakedAmounts;

	private RadixEngineStakeComputerImpl(RRI stakingToken, ImmutableMap<ECPublicKey, UInt256> stakedAmounts) {
		this.stakingToken = stakingToken;
		this.stakedAmounts = stakedAmounts;
	}

	public static RadixEngineStakeComputer create(RRI stakingToken) {
		Objects.requireNonNull(stakingToken);
		return new RadixEngineStakeComputerImpl(stakingToken, ImmutableMap.of());
	}

	private RadixEngineStakeComputerImpl next(ImmutableMap<ECPublicKey, UInt256> stakedAmounts) {
		return new RadixEngineStakeComputerImpl(this.stakingToken, stakedAmounts);
	}

	@Override
	public RadixEngineStakeComputerImpl addStake(RadixAddress delegatedAddress, RRI token, UInt256 amount) {
		if (this.stakingToken.equals(token) && !amount.isZero()) {
			final var delegatedKey = delegatedAddress.getPublicKey();
			final var nextAmount = this.stakedAmounts.getOrDefault(delegatedKey, UInt256.ZERO).add(amount);
			final var nextStakedAmounts = Stream.concat(
				Stream.of(Maps.immutableEntry(delegatedKey, nextAmount)),
				this.stakedAmounts.entrySet().stream().filter(e -> !delegatedKey.equals(e.getKey()))
			).collect(ImmutableMap.toImmutableMap(Map.Entry::getKey, Map.Entry::getValue));
			return next(nextStakedAmounts);
		}
		return this;
	}

	@Override
	public RadixEngineStakeComputerImpl removeStake(RadixAddress delegatedAddress, RRI token, UInt256 amount) {
		final var delegatedKey = delegatedAddress.getPublicKey();
		if (this.stakingToken.equals(token) && !amount.isZero() && this.stakedAmounts.containsKey(delegatedKey)) {
			final var oldAmount = this.stakedAmounts.get(delegatedKey);
			if (amount.compareTo(oldAmount) >= 0) {
				// remove stake
				final var nextStakedAmounts = this.stakedAmounts.entrySet().stream()
					.filter(e -> !delegatedKey.equals(e.getKey()))
					.collect(ImmutableMap.toImmutableMap(Map.Entry::getKey, Map.Entry::getValue));
				return next(nextStakedAmounts);
			} else {
				// reduce stake
				final var nextAmount = oldAmount.subtract(amount);
				final var nextStakedAmounts = Stream.concat(
					Stream.of(Maps.immutableEntry(delegatedKey, nextAmount)),
					this.stakedAmounts.entrySet().stream().filter(e -> !delegatedKey.equals(e.getKey()))
				).collect(ImmutableMap.toImmutableMap(Map.Entry::getKey, Map.Entry::getValue));
				return next(nextStakedAmounts);
			}
		}
		return this;
	}

	@Override
	public ImmutableMap<ECPublicKey, UInt256> stakedAmounts(ImmutableSet<ECPublicKey> validators) {
		return ImmutableMap.copyOf(Maps.filterKeys(this.stakedAmounts, validators::contains));
	}

	@Override
	public String toString() {
		return String.format("%s[%s:%s]", getClass().getSimpleName(), this.stakingToken, this.stakedAmounts);
	}
}
