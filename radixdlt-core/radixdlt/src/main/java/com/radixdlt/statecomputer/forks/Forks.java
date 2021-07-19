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
import com.google.common.collect.Streams;
import com.google.common.hash.HashCode;
import com.radixdlt.application.validators.state.ValidatorSystemMetadata;
import com.radixdlt.consensus.LedgerProof;
import com.radixdlt.constraintmachine.SubstateDeserialization;
import com.radixdlt.engine.parser.REParser;
import com.radixdlt.serialization.DeserializeException;
import com.radixdlt.statecomputer.LedgerAndBFTProof;
import com.radixdlt.sync.CommittedReader;
import com.radixdlt.utils.UInt256;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Manages forks and their transitions. There are two kinds of forks:
 * - a list forks executed at fixed epochs
 * - an optional candidate fork that is switched on based on a predicate (in most cases stake voting)
 *
 * All forks must be executed in order, and a candidate fork can only be considered
 * when we're already running the latest fixed epoch fork.
 */
public final class Forks {
	private static final Logger log = LogManager.getLogger();

	private final ImmutableList<FixedEpochForkConfig> fixedEpochForks;
	private final Optional<CandidateForkConfig> candidateFork;

	public static Forks create(Set<ForkConfig> forks) {
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
			.sorted((a, b) -> (int) (a.epoch() - b.epoch()))
			.collect(ImmutableList.toImmutableList());

		if (fixedEpochForks.isEmpty()) {
			throw new IllegalArgumentException("At least one fork config at fixed epoch is required");
		}

		if (fixedEpochForks.get(0).epoch() != 0L) {
			throw new IllegalArgumentException("Genesis fork must start at epoch 0");
		}

		if (!sanityCheckFixedEpochs(fixedEpochForks)) {
			throw new IllegalArgumentException("Invalid forks: duplicate epoch. " + fixedEpochForks);
		}

		final var latestFixedEpochFork = fixedEpochForks.get(fixedEpochForks.size() - 1);

		if (maybeCandidateFork.isPresent()
			&& maybeCandidateFork.get().minEpoch() <= latestFixedEpochFork.epoch()) {
			throw new IllegalArgumentException("Candidate fork's minEpoch must be greater than the last fixed fork epoch.");
		}

		return new Forks(fixedEpochForks, maybeCandidateFork);
	}

	private static boolean ensureUniqueHashes(Set<ForkConfig> forks) {
		final var hashesSet = forks.stream()
			.map(ForkConfig::hash)
			.collect(Collectors.toSet());
		return forks.size() == hashesSet.size();
	}

	private static boolean sanityCheckFixedEpochs(ImmutableList<FixedEpochForkConfig> forkConfigs) {
		FixedEpochForkConfig prev = null;
		for (var i = forkConfigs.iterator(); i.hasNext();) {
			final var el = i.next();
			if (prev != null && prev.epoch() >= el.epoch()) {
				return false;
			}
			prev = el;
		}
		return true;
	}

	private Forks(
		ImmutableList<FixedEpochForkConfig> fixedEpochForks,
		Optional<CandidateForkConfig> candidateFork
	) {
		// decorate base BatchVerifier with ForksVerifier
		this.fixedEpochForks = IntStream.range(0, fixedEpochForks.size())
			.mapToObj(idx -> {
				final var forkConfig = fixedEpochForks.get(idx);
				if (idx < fixedEpochForks.size() - 1) {
					return forkConfig.withForksVerifier(fixedEpochForks.get(idx + 1));
				} else if (candidateFork.isPresent()) {
					return forkConfig.withForksVerifier(candidateFork.get());
				} else {
					return forkConfig;
				}
			})
			.collect(ImmutableList.toImmutableList());

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
			.filter(forkConfig -> forkConfig.hash().equals(forkHash))
			.findFirst();

		if (maybeFixedEpochFork.isPresent()) {
			// thank you Java for a non-covariant Optional type...
			return (Optional) maybeFixedEpochFork;
		} else {
			return (Optional) candidateFork
				.filter(forkConfig -> forkConfig.hash().equals(forkHash));
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

	public void init(CommittedReader committedReader, ForksEpochStore forksEpochStore) {
		final var initialStoredForks = forksEpochStore.getEpochsForkHashes();
		final var currentEpoch = committedReader.getLastProof().map(LedgerProof::getEpoch).orElse(0L);

		log.info("Forks init [stored_forks: {}, configured_forks: {}]", initialStoredForks, forkConfigs());

		executeMissedFixedEpochForks(initialStoredForks, currentEpoch, forksEpochStore);
		executeAndCheckMissedCandidateFork(initialStoredForks, currentEpoch, forksEpochStore, committedReader);

		sanityCheck(forksEpochStore, currentEpoch);
	}

	private void executeMissedFixedEpochForks(ImmutableMap<Long, HashCode> storedForks, long currentEpoch, ForksEpochStore forksEpochStore) {
		fixedEpochForks.forEach(fixedEpochFork -> {
			final var forkAlreadyStored =
				storedForks.entrySet().stream().anyMatch(e -> e.getValue().equals(fixedEpochFork.hash()));

			// simply store the fork if not already in the database
			// we do not check if the epoch matches here, that'll be caught by sanityCheck
			if (currentEpoch >= fixedEpochFork.epoch() && !forkAlreadyStored) {
				log.info("Found a missed fork config {}, inserting at epoch {}", fixedEpochFork.name(), fixedEpochFork);
				forksEpochStore.storeEpochForkHash(fixedEpochFork.epoch(), fixedEpochFork.hash());
			}
		});
	}

	private void executeAndCheckMissedCandidateFork(
		ImmutableMap<Long, HashCode> storedForks,
		long currentEpoch,
		ForksEpochStore forksEpochStore,
		CommittedReader committedReader
	) {
		if (candidateFork.isEmpty()) {
			return;
		}

		final var candidate = candidateFork.get();
		final var maybeCandidateForkEpoch = findExecuteEpochForCandidate(currentEpoch, forksEpochStore, committedReader);
		final var maybeStoredCandidate =
			storedForks.entrySet().stream().filter(e -> e.getValue().equals(candidate.hash())).findAny();

		maybeCandidateForkEpoch.ifPresentOrElse(
			executeEpoch -> {
				if (maybeStoredCandidate.isPresent()) {
					if (!maybeStoredCandidate.get().getKey().equals(executeEpoch)) {
						throw new IllegalStateException(String.format(
							"Forks inconsistency! Candidate fork should have been executed at epoch %s, but was at %s.",
							executeEpoch,
							maybeStoredCandidate.get().getKey()
						));
					}
				} else {
					log.info("Found a missed candidate fork config {}, inserting at epoch {}", candidate.name(), executeEpoch);
					forksEpochStore.storeEpochForkHash(executeEpoch, candidate.hash());
				}
			},
			() -> {
				if (maybeStoredCandidate.isPresent()) {
					throw new IllegalStateException(String.format(
						"Forks inconsistency! Candidate fork shouldn't have been executed but was at epoch %s.",
						maybeStoredCandidate.get().getKey()
					));
				}
			}
		);
	}

	private Optional<Long> findExecuteEpochForCandidate(
		long currentEpoch,
		ForksEpochStore forksEpochStore,
		CommittedReader committedReader
	) {
		if (candidateFork.isEmpty()) {
			return Optional.empty();
		}

		for (long epoch = candidateFork.get().minEpoch(); epoch <= currentEpoch; epoch++) {
			final var e = epoch;
			final var forkAtEpoch = fixedEpochForks.reverse().stream()
				.filter(fork -> e >= fork.epoch()).findFirst().orElseThrow();
			final var substateDeserialization = forkAtEpoch.engineRules().getParser().getSubstateDeserialization();
			try (var cursor = forksEpochStore.validatorsSystemMetadataCursor(epoch)) {
				final var validatorsMetadataAtEpoch = Streams.stream(cursor)
					.map(data -> deserializeValidatorSystemMetadata(substateDeserialization, data.asBytes()))
					.collect(ImmutableList.toImmutableList());

				if (testCandidate(candidateFork.get(), committedReader.getEpochProof(epoch).orElseThrow(), validatorsMetadataAtEpoch)) {
					return Optional.of(epoch);
				}
			}
		}

		return Optional.empty();
	}

	private void sanityCheck(ForksEpochStore forksEpochStore, long currentEpoch) {
		final var storedForks = forksEpochStore.getEpochsForkHashes();
		final var fixedEpochForksMap = fixedEpochForks.stream()
			.collect(ImmutableMap.toImmutableMap(FixedEpochForkConfig::epoch, Function.identity()));

		fixedEpochForks.stream()
			.filter(f -> f.epoch() <= currentEpoch && f.epoch() > 0)
			.forEach(fork -> {
				final var maybeStored = Optional.ofNullable(storedForks.get(fork.epoch()));
				if (maybeStored.isEmpty() || !maybeStored.get().equals(fork.hash())) {
					throw new IllegalStateException(String.format(
						"Forks inconsistency! Fork %s should have been executed at epoch %s, but wasn't.",
						fork.name(), fork.epoch()
					));
				}
			});

		storedForks.entrySet().stream()
			.filter(e -> e.getKey() <= currentEpoch)
			.forEach(e -> {
				final var maybeExpectedAtFixedEpoch =
					Optional.ofNullable(fixedEpochForksMap.get(e.getKey()));

				final var maybeExpectedCandidate = candidateFork
					.filter(f -> e.getKey() >= f.minEpoch());

				final var expectedAtFixedEpochMatches =
					maybeExpectedAtFixedEpoch.isPresent() && maybeExpectedAtFixedEpoch.get().hash().equals(e.getValue());

				final var expectedCandidateMatches =
					maybeExpectedCandidate.isPresent() && maybeExpectedCandidate.get().hash().equals(e.getValue());

				if (!expectedAtFixedEpochMatches && !expectedCandidateMatches) {
					throw new IllegalStateException(String.format(
						"Forks inconsistency! Fork %s was executed at epoch %s, but shouldn't have been.",
						e.getValue(), e.getKey()
					));
				}
			});
	}

	public ForkConfig getCurrentFork(ImmutableMap<Long, HashCode> storedForks) {
		final var maybeLatestForkHash = storedForks.entrySet().stream()
			.max((a, b) -> (int) (a.getKey() - b.getKey()))
			.map(Map.Entry::getValue);

		return maybeLatestForkHash
			.flatMap(this::getByHash)
			.orElseGet(this::genesisFork);
	}

	public static boolean testCandidate(CandidateForkConfig candidateFork, REParser reParser, LedgerAndBFTProof ledgerAndBFTProof) {
		if (ledgerAndBFTProof.getValidatorsSystemMetadata().isEmpty()) {
			return false;
		}

		final var substateDeserialization = reParser.getSubstateDeserialization();
		final var validatorsSystemMetadata = ledgerAndBFTProof.getValidatorsSystemMetadata().get().stream()
			.map(s -> deserializeValidatorSystemMetadata(substateDeserialization, s.getData()))
			.collect(ImmutableList.toImmutableList());

		return testCandidate(candidateFork, ledgerAndBFTProof.getProof(), validatorsSystemMetadata);
	}

	public static boolean testCandidate(
		CandidateForkConfig candidateFork,
		LedgerProof ledgerProof,
		ImmutableList<ValidatorSystemMetadata> validatorsMetadata
	) {
		if (ledgerProof.getNextValidatorSet().isEmpty()) {
			return false;
		}

		final var nextEpoch = ledgerProof.getEpoch() + 1;
		if (nextEpoch < candidateFork.minEpoch()) {
			return false;
		}

		final var validatorSet = ledgerProof.getNextValidatorSet().get();

		final var requiredPower = validatorSet.getTotalPower().multiply(UInt256.from(candidateFork.requiredStake()))
			.divide(UInt256.from(10000));

		final var forkVotesPower = validatorsMetadata.stream()
			.filter(vm -> validatorSet.containsNode(vm.getValidatorKey()))
			.filter(vm -> {
				final var expectedVoteHash = ForkConfig.voteHash(vm.getValidatorKey(), candidateFork);
				return vm.getAsHash().equals(expectedVoteHash);
			})
			.map(validatorMetadata -> validatorSet.getPower(validatorMetadata.getValidatorKey()))
			.reduce(UInt256.ZERO, UInt256::add);

		return forkVotesPower.compareTo(requiredPower) >= 0;
	}

	private static ValidatorSystemMetadata deserializeValidatorSystemMetadata(
		SubstateDeserialization substateDeserialization,
		byte[] data
	) {
		try {
			return (ValidatorSystemMetadata) substateDeserialization.deserialize(data);
		} catch (DeserializeException e) {
			throw new IllegalStateException("Failed to deserialize ValidatorSystemMetadata substate");
		}
	}
}
