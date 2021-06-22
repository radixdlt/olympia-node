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
import com.radixdlt.engine.RadixEngine;
import com.radixdlt.statecomputer.LedgerAndBFTProof;
import com.radixdlt.utils.Triplet;

import java.util.Objects;
import java.util.Optional;

public final class ForkManager {
	private final ImmutableList<ForkConfig> forksConfigs;

	public ForkManager(ImmutableList<ForkConfig> forksConfigs) {
		if (Objects.requireNonNull(forksConfigs).isEmpty()) {
			throw new IllegalArgumentException("At least one fork config is required");
		}
		this.forksConfigs = forksConfigs;
	}

	public ForkConfig latestKnownFork() {
		return forksConfigs.get(forksConfigs.size() - 1);
	}

	public ImmutableList<ForkConfig> forksConfigs() {
		return this.forksConfigs;
	}

	public Optional<ForkConfig> getByHash(HashCode forkHash) {
		return this.forksConfigs.stream()
			.filter(forkConfig -> forkConfig.getHash().equals(forkHash))
			.findFirst();
	}

	public ForkConfig genesisFork() {
		return this.forksConfigs.get(0);
	}

	public Optional<ForkConfig> findNextForkConfig(
		ForkConfig currentForkConfig,
		RadixEngine<LedgerAndBFTProof> radixEngine,
		LedgerAndBFTProof uncommittedProof
	) {
		final var currentForkIndex = this.forksConfigs.indexOf(currentForkConfig);
		if (currentForkIndex < 0) {
			return Optional.empty();
		}

		final var remainingForks = this.forksConfigs.subList(
			currentForkIndex + 1,
			this.forksConfigs.size()
		);
		return remainingForks
			.reverse()
			.stream()
			.filter(forkConfig -> forkConfig.getExecutePredicate().test(
				Triplet.of(forkConfig, radixEngine, uncommittedProof)))
			.findFirst();
	}
}
