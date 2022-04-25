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

import static com.radixdlt.utils.TypedMocks.rmock;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.radixdlt.consensus.LedgerHeader;
import com.radixdlt.consensus.LedgerProof;
import com.radixdlt.consensus.TimestampedECDSASignatures;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.BFTValidatorSet;
import com.radixdlt.monitoring.SystemCounters;
import com.radixdlt.crypto.HashUtils;
import com.radixdlt.environment.RemoteEventDispatcher;
import com.radixdlt.ledger.AccumulatorState;
import com.radixdlt.ledger.DtoLedgerProof;
import com.radixdlt.ledger.LedgerUpdate;
import com.radixdlt.ledger.VerifiedTxnsAndProof;
import com.radixdlt.network.p2p.PeersView;
import com.radixdlt.sync.messages.remote.LedgerStatusUpdate;
import com.radixdlt.sync.messages.remote.StatusResponse;
import com.radixdlt.sync.messages.remote.SyncRequest;
import com.radixdlt.sync.messages.remote.SyncResponse;
import java.util.Comparator;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;

public class RemoteSyncServiceTest {

  private RemoteSyncService processor;
  private PeersView peersView;
  private LocalSyncService localSyncService;
  private CommittedReader reader;
  private RemoteEventDispatcher<StatusResponse> statusResponseDispatcher;
  private RemoteEventDispatcher<SyncResponse> syncResponseDispatcher;
  private RemoteEventDispatcher<LedgerStatusUpdate> statusUpdateDispatcher;

  @Before
  public void setUp() {
    this.peersView = mock(PeersView.class);
    this.localSyncService = mock(LocalSyncService.class);
    this.reader = mock(CommittedReader.class);
    this.statusResponseDispatcher = rmock(RemoteEventDispatcher.class);
    this.syncResponseDispatcher = rmock(RemoteEventDispatcher.class);
    this.statusUpdateDispatcher = rmock(RemoteEventDispatcher.class);

    final var initialHeader = mock(LedgerProof.class);
    final var initialAccumulatorState = mock(AccumulatorState.class);
    when(initialHeader.getAccumulatorState()).thenReturn(initialAccumulatorState);
    when(initialAccumulatorState.getStateVersion()).thenReturn(1L);

    this.processor =
        new RemoteSyncService(
            peersView,
            localSyncService,
            reader,
            statusResponseDispatcher,
            syncResponseDispatcher,
            statusUpdateDispatcher,
            SyncConfig.of(5000L, 10, 5000L, 10, 50),
            mock(SystemCounters.class),
            Comparator.comparingLong(AccumulatorState::getStateVersion),
            initialHeader);
  }

  @Test
  public void when_remote_sync_request__then_process_it() {
    SyncRequest request = mock(SyncRequest.class);
    DtoLedgerProof header = mock(DtoLedgerProof.class);
    when(header.getOpaque()).thenReturn(HashUtils.zero256());
    when(header.getLedgerHeader()).thenReturn(mock(LedgerHeader.class));
    when(header.getSignatures()).thenReturn(mock(TimestampedECDSASignatures.class));
    when(request.getHeader()).thenReturn(header);
    BFTNode node = mock(BFTNode.class);
    VerifiedTxnsAndProof verifiedTxnsAndProof = mock(VerifiedTxnsAndProof.class);
    LedgerProof verifiedHeader = mock(LedgerProof.class);
    when(verifiedHeader.toDto()).thenReturn(header);
    when(verifiedTxnsAndProof.getProof()).thenReturn(verifiedHeader);
    when(reader.getNextCommittedTxns(any())).thenReturn(verifiedTxnsAndProof);
    processor.syncRequestEventProcessor().process(node, SyncRequest.create(header));
    verify(syncResponseDispatcher, times(1)).dispatch(eq(node), any());
  }

  @Test(expected = NullPointerException.class)
  public void when_bad_remote_sync_request__then_throw_NPE() {
    var node = mock(BFTNode.class);
    var verifiedTxnsAndProof = mock(VerifiedTxnsAndProof.class);
    var verifiedHeader = mock(LedgerProof.class);
    when(verifiedTxnsAndProof.getProof()).thenReturn(verifiedHeader);
    when(reader.getNextCommittedTxns(any())).thenReturn(verifiedTxnsAndProof);

    processor.syncRequestEventProcessor().process(node, SyncRequest.create(null));
    verify(syncResponseDispatcher, times(1)).dispatch(eq(node), any());
  }

  @Test
  public void when_remote_sync_request_and_unable__then_dont_do_anything() {
    SyncRequest request = mock(SyncRequest.class);
    DtoLedgerProof header = mock(DtoLedgerProof.class);
    when(header.getOpaque()).thenReturn(HashUtils.zero256());
    when(header.getLedgerHeader()).thenReturn(mock(LedgerHeader.class));
    when(header.getSignatures()).thenReturn(mock(TimestampedECDSASignatures.class));
    when(request.getHeader()).thenReturn(header);
    processor.syncRequestEventProcessor().process(BFTNode.random(), SyncRequest.create(header));
    verify(syncResponseDispatcher, never()).dispatch(any(BFTNode.class), any());
  }

  @Test
  public void when_remote_sync_request_and_null_return__then_dont_do_anything() {
    DtoLedgerProof header = mock(DtoLedgerProof.class);
    when(header.getOpaque()).thenReturn(HashUtils.zero256());
    when(header.getLedgerHeader()).thenReturn(mock(LedgerHeader.class));
    when(header.getSignatures()).thenReturn(mock(TimestampedECDSASignatures.class));
    processor.syncRequestEventProcessor().process(BFTNode.random(), SyncRequest.create(header));
    when(reader.getNextCommittedTxns(any())).thenReturn(null);
    verify(syncResponseDispatcher, never()).dispatch(any(BFTNode.class), any());
  }

  @Test
  public void when_ledger_update_but_syncing__then_dont_send_status_update() {
    final var tail = mock(LedgerProof.class);
    final var ledgerUpdate = mock(LedgerUpdate.class);
    final var accumulatorState = mock(AccumulatorState.class);
    when(accumulatorState.getStateVersion()).thenReturn(2L);
    when(tail.getAccumulatorState()).thenReturn(accumulatorState);
    when(ledgerUpdate.getTail()).thenReturn(tail);

    final var validatorSet = mock(BFTValidatorSet.class);
    when(ledgerUpdate.getNextValidatorSet()).thenReturn(Optional.of(validatorSet));

    when(this.localSyncService.getSyncState())
        .thenReturn(
            SyncState.SyncingState.init(
                mock(LedgerProof.class), ImmutableList.of(), mock(LedgerProof.class)));

    verifyNoMoreInteractions(peersView);
    verifyNoMoreInteractions(statusUpdateDispatcher);
  }
}
