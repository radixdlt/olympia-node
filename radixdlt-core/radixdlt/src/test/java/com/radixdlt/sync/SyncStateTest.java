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

package com.radixdlt.sync;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.hash.HashCode;
import com.radixdlt.consensus.LedgerProof;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.crypto.HashUtils;
import com.radixdlt.sync.messages.remote.StatusResponse;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.Test;

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

    EqualsVerifier.forClass(SyncState.PendingRequest.class)
        .withPrefabValues(HashCode.class, HashUtils.random256(), HashUtils.random256())
        .verify();
  }

  @Test
  public void idle_state_should_update_current_header() {
    final var peer = mock(BFTNode.class);

    final var initialState = SyncState.IdleState.init(mock(LedgerProof.class));

    final var header2 = mock(LedgerProof.class);
    final var newState = initialState.withCurrentHeader(header2);

    assertEquals(header2, newState.getCurrentHeader());
  }

  @Test
  public void sync_check_state_should_update_current_header() {
    final var peer = mock(BFTNode.class);

    final var initialState =
        SyncState.SyncCheckState.init(mock(LedgerProof.class), ImmutableSet.of(peer));

    final var header2 = mock(LedgerProof.class);
    final var newState = initialState.withCurrentHeader(header2);

    assertEquals(header2, newState.getCurrentHeader());
  }

  @Test
  public void sync_check_state_should_add_status_response() {
    final var peer = mock(BFTNode.class);

    final var initialState =
        SyncState.SyncCheckState.init(mock(LedgerProof.class), ImmutableSet.of(peer));

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
    final var targetHeader = mock(LedgerProof.class);
    final var initialState =
        SyncState.SyncingState.init(mock(LedgerProof.class), ImmutableList.of(), targetHeader);

    final var header2 = mock(LedgerProof.class);
    final var newState = initialState.withCurrentHeader(header2);

    assertEquals(header2, newState.getCurrentHeader());
  }

  @Test
  public void syncing_state_should_update_waiting_for_peer() {
    final var targetHeader = mock(LedgerProof.class);
    final var initialState =
        SyncState.SyncingState.init(mock(LedgerProof.class), ImmutableList.of(), targetHeader);

    assertFalse(initialState.waitingForResponse());

    final var peer = mock(BFTNode.class);
    final var newState = initialState.withPendingRequest(peer, 1L);

    assertTrue(newState.waitingForResponse());
    assertTrue(newState.waitingForResponseFrom(peer));

    assertFalse(newState.clearPendingRequest().waitingForResponse());
  }

  @Test
  public void syncing_state_should_update_candidate_peers() {
    final var targetHeader = mock(LedgerProof.class);
    final var initialState =
        SyncState.SyncingState.init(mock(LedgerProof.class), ImmutableList.of(), targetHeader);

    // there's no next candidate peer
    assertTrue(initialState.fetchNextCandidatePeer().getSecond().isEmpty());

    final var candidate1 = mock(BFTNode.class);
    final var candidate2 = mock(BFTNode.class);
    final var stateWithCandidates =
        initialState.addCandidatePeers(ImmutableList.of(candidate1, candidate2));

    assertEquals(candidate1, stateWithCandidates.peekNthCandidate(0).get());
    assertEquals(candidate2, stateWithCandidates.peekNthCandidate(1).get());

    final var withRemovedCandidate = stateWithCandidates.removeCandidate(candidate1);
    assertEquals(candidate2, withRemovedCandidate.peekNthCandidate(0).get());
    assertEquals(candidate2, withRemovedCandidate.peekNthCandidate(1).get());
  }

  @Test
  public void syncing_state_should_update_target_header() {
    final var targetHeader1 = mock(LedgerProof.class);
    final var targetHeader2 = mock(LedgerProof.class);
    final var initialState =
        SyncState.SyncingState.init(mock(LedgerProof.class), ImmutableList.of(), targetHeader1);

    assertEquals(targetHeader1, initialState.getTargetHeader());

    final var newState = initialState.withTargetHeader(targetHeader2);

    assertEquals(targetHeader2, newState.getTargetHeader());
  }
}
