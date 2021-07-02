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
import com.google.common.collect.ImmutableMap;
import com.google.common.hash.HashCode;
import com.radixdlt.statecomputer.LedgerAndBFTProof;
import com.radixdlt.store.EngineStore;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Manages forks and their transitions. There are two kinds of forks:
 * - a list forks executed at fixed epochs
 * - an optional candidate fork that is switched on based on a predicate (in most cases stake voting)
 *
 * All forks must be executed in order, and a candidate fork can only be considered
 * when we're already running the latest fixed epoch fork.
 */
public final class ForkManager {
	private static final Logger log = LogManager.getLogger();

	private final ImmutableList<FixedEpochForkConfig> fixedEpochForks;
	private final Optional<CandidateForkConfig> candidateFork;

	public static ForkManager create(Set<ForkConfig> forks) {
		if (!ensureUniqueHashes(forks)) {
			throw new IllegalArgumentException("Forks contain duplicate hashes: " + forks);
		}

		final var candidateForks = forks.stream()
			.filter(CandidateForkConfig.class::isInstance)
			.map(CandidateForkConfig.class::cast)
			.collect(ImmutableList.toImmutableList());

		if (candidateForks.size() > 1) {
			throw new IllegalArgumentException("Only a single candidate fork is allowed but got " + candidateForks);
		}

		final var maybeCandidateFork = candidateForks.stream().findAny();

		final var fixedEpochForks = forks.stream()
			.filter(FixedEpochForkConfig.class::isInstance)
			.map(FixedEpochForkConfig.class::cast)
			.sorted((a, b) -> (int) (a.getEpoch() - b.getEpoch()))
			.collect(ImmutableList.toImmutableList());

		if (fixedEpochForks.isEmpty()) {
			throw new IllegalArgumentException("At least one fork config at fixed epoch is required");
		}

		if (fixedEpochForks.get(0).getEpoch() != 0L) {
			throw new IllegalArgumentException("Genesis fork must start at epoch 0");
		}

		if (!sanityCheckFixedEpochs(fixedEpochForks)) {
			throw new IllegalArgumentException("Invalid forks: duplicate epoch. " + fixedEpochForks);
		}

		final var latestFixedEpochFork = fixedEpochForks.get(fixedEpochForks.size() - 1);

		if (maybeCandidateFork.isPresent()
			&& maybeCandidateFork.get().getPredicate().minEpoch() <= latestFixedEpochFork.getEpoch()) {
			throw new IllegalArgumentException("Candidate fork's minEpoch must be greater than the last fixed fork epoch.");
		}

		return new ForkManager(fixedEpochForks, maybeCandidateFork);
	}

	private static boolean ensureUniqueHashes(Set<ForkConfig> forks) {
		final var hashesSet = forks.stream()
			.map(ForkConfig::getHash)
			.collect(Collectors.toSet());
		return forks.size() == hashesSet.size();
	}

	private static boolean sanityCheckFixedEpochs(ImmutableList<FixedEpochForkConfig> forkConfigs) {
		FixedEpochForkConfig prev = null;
		for (var i = forkConfigs.iterator(); i.hasNext();) {
			final var el = i.next();
			if (prev != null && prev.getEpoch() >= el.getEpoch()) {
				return false;
			}
			prev = el;
		}
		return true;
	}

	private ForkManager(
		ImmutableList<FixedEpochForkConfig> fixedEpochForks,
		Optional<CandidateForkConfig> candidateFork
	) {
		this.fixedEpochForks = fixedEpochForks;
		this.candidateFork = candidateFork;
	}

	public Optional<CandidateForkConfig> getCandidateFork() {
		return this.candidateFork;
	}

	public ImmutableList<ForkConfig> forkConfigs() {
		final var builder = ImmutableList.<ForkConfig>builder()
			.addAll(fixedEpochForks);
		candidateFork.ifPresent(builder::add);
		return builder.build();
	}

	@SuppressWarnings("unchecked")
	public Optional<ForkConfig> getByHash(HashCode forkHash) {
		final var maybeFixedEpochFork = this.fixedEpochForks.stream()
			.filter(forkConfig -> forkConfig.getHash().equals(forkHash))
			.findFirst();

		if (maybeFixedEpochFork.isPresent()) {
			// thank you Java for a non-covariant Optional type...
			return (Optional) maybeFixedEpochFork;
		} else {
			return (Optional) candidateFork
				.filter(forkConfig -> forkConfig.getHash().equals(forkHash));
		}
	}

	public ForkConfig genesisFork() {
		return this.fixedEpochForks.get(0);
	}

	public ForkConfig latestKnownFork() {
		if (candidateFork.isPresent()) {
			return candidateFork.get();
		} else {
			return fixedEpochForks.get(fixedEpochForks.size() - 1);
		}
	}

	public ForkConfig sanityCheckForksAndGetInitial(ImmutableMap<Long, HashCode> storedForks, long currentEpoch) {
		final var fixedEpochForksMap = fixedEpochForks.stream()
			.collect(ImmutableMap.toImmutableMap(FixedEpochForkConfig::getEpoch, Function.identity()));

		// TODO: this should also include a check for the candidate fork
		// i.e. if there are enough votes on ledger at any point after `minEpoch`, then the fork should have been executed
		final var expectedForksAreStored = fixedEpochForks.stream()
			.filter(f -> f.getEpoch() <= currentEpoch && f.getEpoch() > 0)
			.allMatch(fork -> {
				final var maybeStored = Optional.ofNullable(storedForks.get(fork.getEpoch()));
				return maybeStored.isPresent() && maybeStored.get().equals(fork.getHash());
			});

		final var storedForksAreExpected = storedForks.entrySet().stream()
			.filter(e -> e.getKey() <= currentEpoch)
			.allMatch(e -> {
				final var maybeExpectedAtFixedEpoch =
					Optional.ofNullable(fixedEpochForksMap.get(e.getKey()));

				final var maybeExpectedCandidate = candidateFork
					.filter(f -> f.getPredicate().minEpoch() >= e.getKey());

				final var expectedAtFixedEpochMatches =
					maybeExpectedAtFixedEpoch.isPresent() && maybeExpectedAtFixedEpoch.get().getHash().equals(e.getValue());

				final var expectedCandidateMatches =
					maybeExpectedCandidate.isPresent() && maybeExpectedCandidate.get().getHash().equals(e.getValue());

				return expectedAtFixedEpochMatches || expectedCandidateMatches;
			});

		if (!expectedForksAreStored) {
			log.warn("Stored forks: {}", storedForks);
			log.warn("Expected forks: {}", forkConfigs());
			throw new RuntimeException("Forks inconsistency! Found a fork config that should have been executed, but wasn't.");
		}

		if (!storedForksAreExpected) {
			log.warn("Stored forks: {}", storedForks);
			log.warn("Expected forks: {}", forkConfigs());
			throw new RuntimeException("Forks inconsistency! Found a fork config that was executed, but shouldn't have been.");
		}

		return getCurrentFork(storedForks);
	}

	public ForkConfig getCurrentFork(ImmutableMap<Long, HashCode> storedForks) {
		final var maybeLatestForkHash = storedForks.entrySet().stream()
			.max((a, b) -> (int) (a.getKey() - b.getKey()))
			.map(Map.Entry::getValue);

		return maybeLatestForkHash
			.flatMap(this::getByHash)
			.orElseGet(this::genesisFork);
	}

	@SuppressWarnings("unchecked")
	public Optional<ForkConfig> findNextForkConfig(
		ForkConfig currentForkConfig,
		EngineStore<LedgerAndBFTProof> engineStore,
		LedgerAndBFTProof ledgerAndBFTProof
	) {
		if (currentForkConfig instanceof CandidateForkConfig) {
			// if we're already running a candidate fork than no action is needed
			return Optional.empty();
		}

		final var nextEpoch = ledgerAndBFTProof.getProof().getEpoch() + 1;
		final var currentFixedEpochFork = (FixedEpochForkConfig) currentForkConfig;
		final var latestFixedEpochFork = fixedEpochForks.get(fixedEpochForks.size() - 1);

		final var maybeNextFixedEpochFork =
			this.fixedEpochForks.stream()
				.filter(f -> f.getEpoch() > currentFixedEpochFork.getEpoch() && nextEpoch == f.getEpoch())
				.findFirst();

		if (maybeNextFixedEpochFork.isPresent()) {
			// move to a next fixed epoch fork, if there is one
			return (Optional) maybeNextFixedEpochFork;
		} else if (currentFixedEpochFork.equals(latestFixedEpochFork)) {
			// if we're at the latest fixed epoch fork, then consider the candidate fork
			final var reParser = currentFixedEpochFork.getEngineRules().getParser();
			return (Optional) candidateFork
				.filter(f ->
					nextEpoch >= f.getPredicate().minEpoch()
						&& f.getPredicate().test(f, engineStore, reParser, ledgerAndBFTProof)
				);
		} else {
			return Optional.empty();
		}
	}
}
