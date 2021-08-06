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
import java.util.Optional;
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
		when(peer1.getRemoteLatestForkHash()).thenReturn(Optional.of(HashCode.fromInt(2))); // this hash is known
		peersForksHashesInfoService.peerEventProcessor().process(PeerEvent.PeerConnected.create(peer1));

		// hash was known, so still empty
		assertTrue(peersForksHashesInfoService.getUnknownReportedForksHashes().isEmpty());

		when(peer1.getRemoteLatestForkHash()).thenReturn(Optional.of(HashCode.fromBytes(new byte[] {0x5})));
		peersForksHashesInfoService.peerEventProcessor().process(PeerEvent.PeerConnected.create(peer1));

		// got an unknown hash from a validator
		assertTrue(peersForksHashesInfoService.getUnknownReportedForksHashes().has("05"));
		assertEquals(
			addressing.forValidators().of(initialValidator.getKey()),
			peersForksHashesInfoService.getUnknownReportedForksHashes().getJSONArray("05").get(0)
		);

		final var peer2 = mock(PeerChannel.class);
		when(peer2.getRemoteNodeId()).thenReturn(NodeId.fromPublicKey(nextValidator.getKey()));
		when(peer2.getRemoteLatestForkHash()).thenReturn(Optional.of(HashCode.fromBytes(new byte[] {0x6})));
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
