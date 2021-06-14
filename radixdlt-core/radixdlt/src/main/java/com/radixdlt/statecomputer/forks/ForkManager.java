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

package com.radixdlt.statecomputer.forks;

import com.google.common.collect.ImmutableList;
import com.google.common.hash.HashCode;
import com.radixdlt.consensus.LedgerProof;

import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

public final class ForkManager {

	private final ImmutableList<ForkConfig> forksConfigs;

	public ForkManager(ImmutableList<ForkConfig> forksConfigs) {
		if (Objects.requireNonNull(forksConfigs).isEmpty()) {
			throw new IllegalArgumentException("At least one fork config is required");
		}
		this.forksConfigs = forksConfigs;
	}

	public ForkConfig currentFork(long epoch, Optional<LedgerProof> maybeEpochProof) {
		return maybeEpochProof.flatMap(this::latestForkByLedgerProof)
			.orElseGet(() -> this.latestForkByFixedEpoch(epoch));
	}

	private Optional<ForkConfig> latestForkByLedgerProof(LedgerProof epochProof) {
		return epochProof.getNextForkHash().stream()
			.flatMap(nextForkHash -> forksConfigs.stream().filter(fc -> fc.getHash().equals(nextForkHash)))
			.findFirst();
	}

	/**
	 * Returns a fork that applies at epoch `epoch`, but could be introduced at earlier epoch.
	 */
	public ForkConfig latestForkByFixedEpoch(long epoch) {
		return forksConfigs.reverse().stream()
			.filter(forkConfig -> forkConfig.getExecutedAtEpoch().filter(e -> e.compareTo(epoch) <= 0).isPresent())
			.findFirst()
			.orElse(forksConfigs.get(0));
	}

	/**
	 * Returns a fork config that is to be introduced exactly at epoch `epoch`.
	 */
	public Optional<ForkConfig> forkAtExactEpoch(long epoch) {
		return forksConfigs.stream()
			.filter(fc -> fc.getExecutedAtEpoch().filter(e -> e.equals(epoch)).isPresent())
			.findFirst();
	}

	public Optional<ForkConfig> getForkByHash(HashCode forkHash) {
		return forksConfigs.stream()
			.filter(forkConfig -> forkConfig.getHash().equals(forkHash))
			.findAny();
	}

	/**
	 * Returns a stream of forks that undergo voting, starting with the most recent forks.
	 */
	public Stream<ForkConfig> votableForksSortedByNewest() {
		return forksConfigs.reverse().stream()
			.filter(forkConfig ->
				// pre betanet4 forks are not votable (are executed at fixed epochs)
				forkConfig.getExecutedAtEpoch().isEmpty()
					&& forkConfig.getRequiredVotingStakePercentage() > 0
			);
	}

	public ForkConfig latestKnownFork() {
		return forksConfigs.get(forksConfigs.size() - 1);
	}

	public ImmutableList<ForkConfig> forksConfigs() {
		return this.forksConfigs;
	}
}
