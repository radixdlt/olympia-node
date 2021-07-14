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
import com.radixdlt.atom.SubstateTypeId;
import com.radixdlt.consensus.LedgerProof;
import com.radixdlt.constraintmachine.SubstateDeserialization;
import com.radixdlt.constraintmachine.SubstateIndex;
import com.radixdlt.engine.parser.REParser;
import com.radixdlt.serialization.DeserializeException;
import com.radixdlt.statecomputer.LedgerAndBFTProof;
import com.radixdlt.store.EngineStore;
import com.radixdlt.sync.CommittedReader;
import com.radixdlt.utils.UInt256;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
			&& maybeCandidateFork.get().minEpoch() <= latestFixedEpochFork.getEpoch()) {
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
			if (prev != null && prev.getEpoch() >= el.getEpoch()) {
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
		/* TODO(fixme): this is a hack to inject Forks to ForkBatchVerifier */
		final var fixedEpochForksWithForkVerifier = fixedEpochForks.stream()
			.map(forkConfig -> forkConfig.withForksVerifier(this))
			.collect(ImmutableList.toImmutableList());

		final var candidateForkWithForkVerifier = candidateFork
			.map(forkConfig -> forkConfig.withForksVerifier(this));

		this.fixedEpochForks = fixedEpochForksWithForkVerifier;
		this.candidateFork = candidateForkWithForkVerifier;
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

	/*
	When the node has failed due to not knowing of a fork, and is later restarted with a never version
	that contains the fork, we need to check if any forks that this node hasn't yet executed should be executed in
	the current epoch.
	 */
	public void tryExecuteMissedFork(
		EngineStore<LedgerAndBFTProof> engineStore,
		CommittedReader committedReader,
		ForksEpochStore forksEpochStore
	) {
		final var maybeLastProof = committedReader.getLastProof();
		final var currentFork = getCurrentFork(forksEpochStore.getEpochsForkHashes());

		// TODO: use validator's system metadata db
		if (!latestKnownFork().hash().equals(currentFork.hash())) {
			maybeLastProof
				.flatMap(lastProof -> committedReader.getEpochProof(lastProof.getEpoch()))
				.map(epochProof -> LedgerAndBFTProof.create(epochProof, null, currentFork.hash()))
				.flatMap(ledgerAndBftProof -> findNextForkConfig(engineStore, ledgerAndBftProof))
				.ifPresent(nextFork -> {
					log.info("Found a missed fork config: {}", nextFork.name());
					forksEpochStore.storeEpochForkHash(maybeLastProof.get().getEpoch(), nextFork.hash());
				});
		}
	}

	public void sanityCheck(CommittedReader committedReader, ForksEpochStore forksEpochStore) {
		final var currentEpoch = committedReader.getLastProof().map(LedgerProof::getEpoch).orElse(0L);
		final var storedForks = forksEpochStore.getEpochsForkHashes();

		log.info("[Forks sanity check] Stored forks: {}", storedForks);
		log.info("[Forks sanity check] Expected forks: {}", forkConfigs());

		final var fixedEpochForksMap = fixedEpochForks.stream()
			.collect(ImmutableMap.toImmutableMap(FixedEpochForkConfig::getEpoch, Function.identity()));

		final var expectedForksAreStored = fixedEpochForks.stream()
			.filter(f -> f.getEpoch() <= currentEpoch && f.getEpoch() > 0)
			.allMatch(fork -> {
				final var maybeStored = Optional.ofNullable(storedForks.get(fork.getEpoch()));
				return maybeStored.isPresent() && maybeStored.get().equals(fork.hash());
			});

		final var storedForksAreExpected = storedForks.entrySet().stream()
			.filter(e -> e.getKey() <= currentEpoch)
			.allMatch(e -> {
				final var maybeExpectedAtFixedEpoch =
					Optional.ofNullable(fixedEpochForksMap.get(e.getKey()));

				final var maybeExpectedCandidate = candidateFork
					.filter(f -> e.getKey() >= f.minEpoch());

				final var expectedAtFixedEpochMatches =
					maybeExpectedAtFixedEpoch.isPresent() && maybeExpectedAtFixedEpoch.get().hash().equals(e.getValue());

				final var expectedCandidateMatches =
					maybeExpectedCandidate.isPresent() && maybeExpectedCandidate.get().hash().equals(e.getValue());

				return expectedAtFixedEpochMatches || expectedCandidateMatches;
			});

		if (!expectedForksAreStored) {
			throw new IllegalStateException("Forks inconsistency! Found a fork config that should have been executed, but wasn't.");
		}

		if (!storedForksAreExpected) {
			throw new IllegalStateException("Forks inconsistency! Found a fork config that was executed, but shouldn't have been.");
		}

		if (candidateFork.isPresent()) {
			// if there is a candidate fork, we need to make sure it has been executed correctly according to stake votes
			for (long epoch = candidateFork.orElseThrow().minEpoch(); epoch <= currentEpoch; epoch++) {
				final var e = epoch;
				final var forkAtEpoch = fixedEpochForks.reverse().stream()
					.filter(fork -> e >= fork.getEpoch()).findFirst().orElseThrow();
				final var substateDeserialization = forkAtEpoch.engineRules().getParser().getSubstateDeserialization();
				try (var cursor = forksEpochStore.validatorsSystemMetadataCursor(epoch)) {
					final var candidateExecuted =
						testCandidate(
							committedReader.getEpochProof(epoch).orElseThrow(),
							Streams.stream(cursor).map(data -> deserializeValidatorSystemMetadata(substateDeserialization, data.asBytes()))
						);
					if (candidateExecuted && !storedForks.get(epoch).equals(candidateFork.get().hash())) {
						throw new IllegalStateException(String.format(
							"Forks inconsistency! Candidate fork should have been executed at epoch %s, but wasn't.", epoch
						));
					}
				}
			}
		}
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
		EngineStore<LedgerAndBFTProof> engineStore,
		LedgerAndBFTProof ledgerAndBFTProof
	) {
		final var currentForkConfig = getByHash(ledgerAndBFTProof.getCurrentForkHash()).get();

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
		} else if (currentFixedEpochFork.hash().equals(latestFixedEpochFork.hash())) {
			// if we're at the latest fixed epoch fork, then consider the candidate fork
			final var reParser = currentFixedEpochFork.engineRules().getParser();
			return (Optional) candidateFork.filter(unused -> testCandidate(engineStore, reParser, ledgerAndBFTProof.getProof()));
		} else {
			return Optional.empty();
		}
	}

	private boolean testCandidate(
		EngineStore<LedgerAndBFTProof> engineStore,
		REParser reParser,
		LedgerProof ledgerProof
	) {
		final var substateDeserialization = reParser.getSubstateDeserialization();
		try (var validatorMetadataCursor = engineStore.openIndexedCursor(
			SubstateIndex.create(SubstateTypeId.VALIDATOR_SYSTEM_META_DATA.id(), ValidatorSystemMetadata.class))
		) {
			return testCandidate(
				ledgerProof,
				Streams.stream(validatorMetadataCursor)
					.map(s -> deserializeValidatorSystemMetadata(substateDeserialization, s.getData()))
			);
		}
	}

	private ValidatorSystemMetadata deserializeValidatorSystemMetadata(
		SubstateDeserialization substateDeserialization,
		byte[] data
	) {
		try {
			return (ValidatorSystemMetadata) substateDeserialization.deserialize(data);
		} catch (DeserializeException e) {
			throw new IllegalStateException("Failed to deserialize ValidatorSystemMetadata substate");
		}
	}

	private boolean testCandidate(LedgerProof ledgerProof, Stream<ValidatorSystemMetadata> validatorsMetadata) {
		if (candidateFork.isEmpty() || ledgerProof.getNextValidatorSet().isEmpty()) {
			return false;
		}

		final var nextEpoch = ledgerProof.getEpoch() + 1;
		if (nextEpoch < candidateFork.get().minEpoch()) {
			return false;
		}

		final var candidate = candidateFork.get();

		final var validatorSet = ledgerProof.getNextValidatorSet().get();

		final var requiredPower = validatorSet.getTotalPower().multiply(UInt256.from(candidate.requiredStake()))
			.divide(UInt256.from(10000));

		final var forkVotesPower = validatorsMetadata
			.filter(vm -> validatorSet.containsNode(vm.getValidatorKey()))
			.filter(vm -> {
				final var expectedVoteHash = ForkConfig.voteHash(vm.getValidatorKey(), candidate);
				return vm.getAsHash().equals(expectedVoteHash);
			})
			.map(validatorMetadata -> validatorSet.getPower(validatorMetadata.getValidatorKey()))
			.reduce(UInt256.ZERO, UInt256::add);

		return forkVotesPower.compareTo(requiredPower) >= 0;
	}
}
