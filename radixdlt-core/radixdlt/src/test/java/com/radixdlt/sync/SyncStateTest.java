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
 */

package com.radixdlt.sync;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.hash.HashCode;
import com.radixdlt.consensus.VerifiedLedgerHeaderAndProof;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.crypto.HashUtils;
import com.radixdlt.sync.messages.remote.StatusResponse;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.Test;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;

public class SyncStateTest {

    @Test
    public void equalsContract() {
        EqualsVerifier.forClass(SyncState.IdleState.class)
            .withPrefabValues(HashCode.class, HashUtils.random256(), HashUtils.random256())
            .verify();

        EqualsVerifier.forClass(SyncState.SyncCheckState.class)
                .withPrefabValues(HashCode.class, HashUtils.random256(), HashUtils.random256())
                .verify();

        EqualsVerifier.forClass(SyncState.SyncingState.class)
                .withPrefabValues(HashCode.class, HashUtils.random256(), HashUtils.random256())
                .verify();
    }

    @Test
    public void idle_state_should_update_current_header() {
        final var peer = mock(BFTNode.class);

        final var initialState = SyncState.IdleState.init(
            mock(VerifiedLedgerHeaderAndProof.class)
        );

        final var header2 = mock(VerifiedLedgerHeaderAndProof.class);
        final var newState = initialState.withCurrentHeader(header2);

        assertEquals(header2, newState.getCurrentHeader());
    }

    @Test
    public void sync_check_state_should_update_current_header() {
        final var peer = mock(BFTNode.class);

        final var initialState = SyncState.SyncCheckState.init(
                mock(VerifiedLedgerHeaderAndProof.class),
                ImmutableSet.of(peer)
        );

        final var header2 = mock(VerifiedLedgerHeaderAndProof.class);
        final var newState = initialState.withCurrentHeader(header2);

        assertEquals(header2, newState.getCurrentHeader());
    }

    @Test
    public void sync_check_state_should_add_status_response() {
        final var peer = mock(BFTNode.class);

        final var initialState = SyncState.SyncCheckState.init(
            mock(VerifiedLedgerHeaderAndProof.class),
            ImmutableSet.of(peer)
        );

        final var statusResponse = mock(StatusResponse.class);
        final var newState = initialState.withStatusResponse(peer, statusResponse);

        assertTrue(newState.receivedResponseFrom(peer));
        assertEquals(1, newState.responses().size());
        assertTrue(newState.gotAllResponses());

        final var otherPeer = mock(BFTNode.class);
        assertFalse(newState.hasAskedPeer(otherPeer));
    }

    @Test
    public void syncing_state_should_update_current_header() {
        final var targetHeader = mock(VerifiedLedgerHeaderAndProof.class);
        final var initialState = SyncState.SyncingState.init(
            mock(VerifiedLedgerHeaderAndProof.class),
            ImmutableList.of(),
            targetHeader
        );

        final var header2 = mock(VerifiedLedgerHeaderAndProof.class);
        final var newState = initialState.withCurrentHeader(header2);

        assertEquals(header2, newState.getCurrentHeader());
    }

    @Test
    public void syncing_state_should_update_waiting_for_peer() {
        final var targetHeader = mock(VerifiedLedgerHeaderAndProof.class);
        final var initialState = SyncState.SyncingState.init(
            mock(VerifiedLedgerHeaderAndProof.class),
            ImmutableList.of(),
            targetHeader
        );

        assertFalse(initialState.waitingForResponse());

        final var peer = mock(BFTNode.class);
        final var newState = initialState.withWaitingFor(peer);

        assertTrue(newState.waitingForResponse());
        assertTrue(newState.waitingForResponseFrom(peer));

        assertFalse(newState.clearWaitingFor().waitingForResponse());
    }

    @Test
    public void syncing_state_should_update_candidate_peers() {
        final var targetHeader = mock(VerifiedLedgerHeaderAndProof.class);
        final var initialState = SyncState.SyncingState.init(
            mock(VerifiedLedgerHeaderAndProof.class),
            ImmutableList.of(),
            targetHeader
        );

        assertEquals(0, initialState.candidatePeers().size());

        final var candidate1 = mock(BFTNode.class);
        final var candidate2 = mock(BFTNode.class);
        final var stateWithCandidates = initialState.withCandidatePeers(
            ImmutableList.of(candidate1, candidate2)
        );

        assertEquals(2, stateWithCandidates.candidatePeers().size());

        final var withRemovedCandidate = stateWithCandidates.removeCandidate(candidate1);
        assertEquals(1, withRemovedCandidate.candidatePeers().size());
        assertEquals(candidate2, withRemovedCandidate.candidatePeers().get(0));
    }

    @Test
    public void syncing_state_should_update_target_header() {
        final var targetHeader1 = mock(VerifiedLedgerHeaderAndProof.class);
        final var targetHeader2 = mock(VerifiedLedgerHeaderAndProof.class);
        final var initialState = SyncState.SyncingState.init(
            mock(VerifiedLedgerHeaderAndProof.class),
            ImmutableList.of(),
            targetHeader1
        );

        assertEquals(targetHeader1, initialState.getTargetHeader());

        final var newState = initialState.withTargetHeader(targetHeader2);

        assertEquals(targetHeader2, newState.getTargetHeader());
    }

}
