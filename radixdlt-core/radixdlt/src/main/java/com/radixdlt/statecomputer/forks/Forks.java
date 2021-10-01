/* Copyright 2021 Radix Publishing Ltd incorporated in Jersey (Channel Islands).
 *
 * Licensed under the Radix License, Version 1.0 (the "License"); you may not use this
 * file except in compliance with the License. You may obtain a copy of the License at:
 *
 * radixfoundation.org/licenses/LICENSE-v1
 *
 * The Licensor hereby grants permission for the Canonical version of the Work to be
 * published, distributed and used under or by reference to the Licensor’s trademark
 * Radix ® and use of any unregistered trade names, logos or get-up.
 *
 * The Licensor provides the Work (and each Contributor provides its Contributions) on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied,
 * including, without limitation, any warranties or conditions of TITLE, NON-INFRINGEMENT,
 * MERCHANTABILITY, or FITNESS FOR A PARTICULAR PURPOSE.
 *
 * Whilst the Work is capable of being deployed, used and adopted (instantiated) to create
 * a distributed ledger it is your responsibility to test and validate the code, together
 * with all logic and performance of that code under all foreseeable scenarios.
 *
 * The Licensor does not make or purport to make and hereby excludes liability for all
 * and any representation, warranty or undertaking in any form whatsoever, whether express
 * or implied, to any entity or person, including any representation, warranty or
 * undertaking, as to the functionality security use, value or other characteristics of
 * any distributed ledger nor in respect the functioning or value of any tokens which may
 * be created stored or transferred using the Work. The Licensor does not warrant that the
 * Work or any use of the Work complies with any law or regulation in any territory where
 * it may be implemented or used or that it will be appropriate for any specific purpose.
 *
 * Neither the licensor nor any current or former employees, officers, directors, partners,
 * trustees, representatives, agents, advisors, contractors, or volunteers of the Licensor
 * shall be liable for any direct or indirect, special, incidental, consequential or other
 * losses of any kind, in tort, contract or otherwise (including but not limited to loss
 * of revenue, income or profits, or loss of use or data, or loss of reputation, or loss
 * of any economic or other opportunity of whatsoever nature or howsoever arising), arising
 * out of or in connection with (without limitation of any use, misuse, of any ledger system
 * or use made or its functionality or any performance or operation of any code or protocol
 * caused by bugs or programming or logic errors or otherwise);
 *
 * A. any offer, purchase, holding, use, sale, exchange or transmission of any
 * cryptographic keys, tokens or assets created, exchanged, stored or arising from any
 * interaction with the Work;
 *
 * B. any failure in a transmission or loss of any token or assets keys or other digital
 * artefacts due to errors in transmission;
 *
 * C. bugs, hacks, logic errors or faults in the Work or any communication;
 *
 * D. system software or apparatus including but not limited to losses caused by errors
 * in holding or transmitting tokens by any third-party;
 *
 * E. breaches or failure of security including hacker attacks, loss or disclosure of
 * password, loss of private key, unauthorised use or misuse of such passwords or keys;
 *
 * F. any losses including loss of anticipated savings or other benefits resulting from
 * use of the Work or any changes to the Work (however implemented).
 *
 * You are solely responsible for; testing, validating and evaluation of all operation
 * logic, functionality, security and appropriateness of using the Work for any commercial
 * or non-commercial purpose and for any reproduction or redistribution by You of the
 * Work. You assume all risks associated with Your use of the Work and the exercise of
 * permissions under this License.
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
import com.radixdlt.statecomputer.NextCandidateForkPostProcessor;
import com.radixdlt.statecomputer.NextFixedEpochForkPostProcessor;
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
 * A container for forks.
 * There are two kinds of forks:
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
		// decorate base PostProcessor with ForksPostProcessor
		this.fixedEpochForks = IntStream.range(0, fixedEpochForks.size())
			.mapToObj(idx -> {
				final var forkConfig = fixedEpochForks.get(idx);
				if (idx < fixedEpochForks.size() - 1) {
					final var nextForkPostProcessor = new NextFixedEpochForkPostProcessor(
						fixedEpochForks.get(idx + 1)
					);
					return forkConfig.addPostProcessor(nextForkPostProcessor);
				} else if (candidateFork.isPresent()) {
					final var nextForkPostProcessor = new NextCandidateForkPostProcessor(
						forkConfig.engineRules().getParser(),
						candidateFork.get()
					);
					return forkConfig.addPostProcessor(nextForkPostProcessor);
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

	public ForkConfig latestFork() {
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

	private static boolean testCandidate(
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
