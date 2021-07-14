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

package com.radixdlt.api.service;

import com.google.common.collect.ImmutableClassToInstanceMap;
import com.google.common.hash.HashCode;
import com.radixdlt.application.validators.state.ValidatorSystemMetadata;
import com.radixdlt.atom.CloseableCursor;
import static com.radixdlt.api.service.ForkVoteStatusService.ForkVoteStatus.NO_ACTION_NEEDED;
import static com.radixdlt.api.service.ForkVoteStatusService.ForkVoteStatus.VOTE_REQUIRED;

import com.radixdlt.consensus.LedgerProof;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.constraintmachine.RawSubstateBytes;
import com.radixdlt.engine.RadixEngine;
import com.radixdlt.ledger.LedgerUpdate;
import com.radixdlt.ledger.VerifiedTxnsAndProof;
import com.radixdlt.statecomputer.LedgerAndBFTProof;
import com.radixdlt.statecomputer.forks.CandidateForkConfig;
import com.radixdlt.statecomputer.forks.FixedEpochForkConfig;
import com.radixdlt.statecomputer.forks.ForkConfig;
import com.radixdlt.statecomputer.forks.Forks;
import com.radixdlt.statecomputer.forks.RERules;
import com.radixdlt.statecomputer.forks.RERulesConfig;
import com.radixdlt.statecomputer.forks.RERulesVersion;
import com.radixdlt.store.EngineStore;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static com.radixdlt.utils.TypedMocks.rmock;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

public final class ForkVoteStatusServiceTest {

	private final RERules reRules = RERulesVersion.OLYMPIA_V1.create(RERulesConfig.testingDefault());

	@Test
	public void should_correctly_tell_if_fork_vote_is_needed() {
		final var self = BFTNode.random();
		final var otherNode = BFTNode.random();
		final var engineStore = (EngineStore<LedgerAndBFTProof>) rmock(EngineStore.class);
		final var initialFork = new FixedEpochForkConfig("fork1", HashCode.fromInt(1), reRules, 0L);
		final var candidateFork = new CandidateForkConfig("fork2", HashCode.fromInt(2), reRules, 5100, 2L);
		final var forks = Forks.create(Set.of(initialFork, candidateFork));

		final var forkVoteStatusService = new ForkVoteStatusService(self, engineStore, forks, initialFork);

		when(engineStore.openIndexedCursor(any())).thenAnswer(unused -> votesOf(candidateFork, otherNode));
		assertEquals(VOTE_REQUIRED, forkVoteStatusService.forkVoteStatus());

		when(engineStore.openIndexedCursor(any())).thenAnswer(unused -> votesOf(candidateFork, self, otherNode));
		assertEquals(NO_ACTION_NEEDED, forkVoteStatusService.forkVoteStatus());
	}

	@Test
	public void should_not_require_a_vote_for_non_candidate_fork() {
		final var self = BFTNode.random();
		final var engineStore = (EngineStore<LedgerAndBFTProof>) rmock(EngineStore.class);
		final var initialFork = new FixedEpochForkConfig("fork1", HashCode.fromInt(1), reRules, 0L);
		final var nextFork = new FixedEpochForkConfig("fork2", HashCode.fromInt(2), reRules, 2L);
		final var forks = Forks.create(Set.of(initialFork, nextFork));

		final var forkVoteStatusService = new ForkVoteStatusService(self, engineStore, forks, initialFork);

		assertEquals(NO_ACTION_NEEDED, forkVoteStatusService.forkVoteStatus());
		verifyNoInteractions(engineStore);
	}

	@Test
	public void should_keep_track_of_current_fork_config() {
		final var self = BFTNode.random();
		final var engineStore = (EngineStore<LedgerAndBFTProof>) rmock(EngineStore.class);
		final var initialFork = new FixedEpochForkConfig("fork1", HashCode.fromInt(1), reRules, 0L);
		final var nextFork = new FixedEpochForkConfig("fork2", HashCode.fromInt(2), reRules, 2L);
		final var forks = Forks.create(Set.of(initialFork, nextFork));

		final var forkVoteStatusService = new ForkVoteStatusService(self, engineStore, forks, initialFork);

		assertEquals("fork1", forkVoteStatusService.currentFork().get("name").toString());

		final var ledgerAndBftProof = LedgerAndBFTProof.create(
			mock(LedgerProof.class),
			null,
			HashCode.fromInt(1),
			Optional.of(HashCode.fromInt(2)),
			Optional.empty()
		);
		final var radixEngineResult = new RadixEngine.RadixEngineResult<>(List.of(), ledgerAndBftProof);
		final var ledgerUpdate = new LedgerUpdate(mock(VerifiedTxnsAndProof.class),
			ImmutableClassToInstanceMap.of(RadixEngine.RadixEngineResult.class, radixEngineResult));
		forkVoteStatusService.ledgerUpdateEventProcessor().process(ledgerUpdate);

		assertEquals("fork2", forkVoteStatusService.currentFork().get("name").toString());
	}

	private CloseableCursor<RawSubstateBytes> votesOf(ForkConfig forkConfig, BFTNode... nodes) {
		return CloseableCursor.of(
			Arrays.stream(nodes)
				.map(n -> voteOf(n, forkConfig.hash()))
				.toArray(RawSubstateBytes[]::new)
		);
	}

	private RawSubstateBytes voteOf(BFTNode validator, HashCode forkHash) {
		final var pubKey = validator.getKey();
		final var substate = new ValidatorSystemMetadata(pubKey, ForkConfig.voteHash(pubKey, forkHash).asBytes());
		final var serializedSubstate = reRules.getSerialization().serialize(substate);
		return new RawSubstateBytes(new byte[] {}, serializedSubstate);
	}
}
