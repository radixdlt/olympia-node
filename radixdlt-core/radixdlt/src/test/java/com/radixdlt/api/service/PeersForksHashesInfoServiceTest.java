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
import com.radixdlt.consensus.BFTConfiguration;
import com.radixdlt.consensus.LedgerProof;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.BFTValidator;
import com.radixdlt.consensus.bft.BFTValidatorSet;
import com.radixdlt.consensus.epoch.EpochChange;
import com.radixdlt.ledger.LedgerUpdate;
import com.radixdlt.ledger.VerifiedTxnsAndProof;
import com.radixdlt.network.p2p.NodeId;
import com.radixdlt.network.p2p.PeerEvent;
import com.radixdlt.network.p2p.transport.PeerChannel;
import com.radixdlt.networks.Addressing;
import com.radixdlt.statecomputer.forks.CandidateForkConfig;
import com.radixdlt.statecomputer.forks.FixedEpochForkConfig;
import com.radixdlt.statecomputer.forks.Forks;
import com.radixdlt.statecomputer.forks.RERules;
import com.radixdlt.statecomputer.forks.RERulesConfig;
import com.radixdlt.statecomputer.forks.RERulesVersion;
import com.radixdlt.utils.UInt256;
import org.junit.Test;

import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public final class PeersForksHashesInfoServiceTest {

	private final RERules reRules = RERulesVersion.OLYMPIA_V1.create(RERulesConfig.testingDefault());
	private final Addressing addressing = Addressing.ofNetworkId(1);

	@Test
	public void should_collect_unknown_forks_hashes_from_peer_events() {
		final var initialFork = new FixedEpochForkConfig("fork1", HashCode.fromInt(1), reRules, 0L);
		final var candidateFork = new CandidateForkConfig("fork2", HashCode.fromInt(2), reRules, 5100, 2L);
		final var forks = Forks.create(Set.of(initialFork, candidateFork));

		final var initialValidator = BFTNode.random();
		final var nextValidator = BFTNode.random();

		final var initialValidators = List.of(BFTValidator.from(initialValidator, UInt256.ONE));
		final var bftConfiguration = mock(BFTConfiguration.class);
		when(bftConfiguration.getValidatorSet()).thenReturn(BFTValidatorSet.from(initialValidators));
		final var initialEpochChange = new EpochChange(mock(LedgerProof.class), bftConfiguration);
		final var peersForksHashesInfoService = new PeersForksHashesInfoService(forks, addressing, initialEpochChange);

		assertTrue(peersForksHashesInfoService.getUnknownReportedForksHashes().isEmpty());

		final var peer1 = mock(PeerChannel.class);
		when(peer1.getRemoteNodeId()).thenReturn(NodeId.fromPublicKey(initialValidator.getKey()));
		/* TODO(luk): fixme */
//		when(peer1.getRemoteLatestKnownForkHash()).thenReturn(HashCode.fromInt(2)); // this hash is known
		peersForksHashesInfoService.peerEventProcessor().process(PeerEvent.PeerConnected.create(peer1));

		// hash was known, so still empty
		assertTrue(peersForksHashesInfoService.getUnknownReportedForksHashes().isEmpty());

		/* TODO(luk): fixme */
//		when(peer1.getRemoteLatestKnownForkHash()).thenReturn(HashCode.fromBytes(new byte[] {0x5}));
		peersForksHashesInfoService.peerEventProcessor().process(PeerEvent.PeerConnected.create(peer1));

		// got an unknown hash from a validator
		assertTrue(peersForksHashesInfoService.getUnknownReportedForksHashes().has("05"));
		assertEquals(
			addressing.forValidators().of(initialValidator.getKey()),
			peersForksHashesInfoService.getUnknownReportedForksHashes().getJSONArray("05").get(0)
		);

		final var peer2 = mock(PeerChannel.class);
		when(peer2.getRemoteNodeId()).thenReturn(NodeId.fromPublicKey(nextValidator.getKey()));
		/* TODO(luk): fixme */
//		when(peer2.getRemoteLatestKnownForkHash()).thenReturn(HashCode.fromBytes(new byte[] {0x6}));
		peersForksHashesInfoService.peerEventProcessor().process(PeerEvent.PeerConnected.create(peer2));

		// got unknown hash from non-validator, so no change
		assertFalse(peersForksHashesInfoService.getUnknownReportedForksHashes().has("06"));

		final var nextValidators = List.of(BFTValidator.from(nextValidator, UInt256.ONE));
		final var newBftConfig = mock(BFTConfiguration.class);
		when(newBftConfig.getValidatorSet()).thenReturn(BFTValidatorSet.from(nextValidators));
		final var newEpochChange = new EpochChange(mock(LedgerProof.class), newBftConfig);
		final var ledgerUpdate = new LedgerUpdate(mock(VerifiedTxnsAndProof.class),
			ImmutableClassToInstanceMap.of(EpochChange.class, newEpochChange));
		peersForksHashesInfoService.ledgerUpdateEventProcessor().process(ledgerUpdate);

		// process unknown hash from the same peer (this time a validator)
		peersForksHashesInfoService.peerEventProcessor().process(PeerEvent.PeerConnected.create(peer2));

		assertTrue(peersForksHashesInfoService.getUnknownReportedForksHashes().has("06"));
		assertEquals(
			addressing.forValidators().of(nextValidator.getKey()),
			peersForksHashesInfoService.getUnknownReportedForksHashes().getJSONArray("06").get(0)
		);
	}
}
