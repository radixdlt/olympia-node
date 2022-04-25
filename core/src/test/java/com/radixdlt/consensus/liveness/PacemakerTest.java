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

package com.radixdlt.consensus.liveness;

import static com.radixdlt.utils.TypedMocks.rmock;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.google.common.collect.ImmutableSet;
import com.google.common.hash.HashCode;
import com.radixdlt.consensus.BFTHeader;
import com.radixdlt.consensus.HighQC;
import com.radixdlt.consensus.LedgerHeader;
import com.radixdlt.consensus.Proposal;
import com.radixdlt.consensus.QuorumCertificate;
import com.radixdlt.consensus.Sha256Hasher;
import com.radixdlt.consensus.UnverifiedVertex;
import com.radixdlt.consensus.Vote;
import com.radixdlt.consensus.bft.BFTInsertUpdate;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.BFTValidatorSet;
import com.radixdlt.consensus.bft.PreparedVertex;
import com.radixdlt.consensus.bft.VerifiedVertex;
import com.radixdlt.consensus.bft.VerifiedVertexStoreState;
import com.radixdlt.consensus.bft.VertexStore;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.consensus.bft.ViewUpdate;
import com.radixdlt.consensus.safety.SafetyRules;
import com.radixdlt.monitoring.SystemCounters;
import com.radixdlt.monitoring.SystemCountersImpl;
import com.radixdlt.crypto.Hasher;
import com.radixdlt.environment.EventDispatcher;
import com.radixdlt.environment.RemoteEventDispatcher;
import com.radixdlt.environment.ScheduledEventDispatcher;
import com.radixdlt.utils.TimeSupplier;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

public class PacemakerTest {

  private static final Hasher hasher = Sha256Hasher.withDefaultSerialization();

  private BFTNode self = mock(BFTNode.class);
  private SystemCounters counters = mock(SystemCounters.class);
  private BFTValidatorSet validatorSet = mock(BFTValidatorSet.class);
  private VertexStore vertexStore = mock(VertexStore.class);
  private SafetyRules safetyRules = mock(SafetyRules.class);
  private PacemakerTimeoutCalculator timeoutCalculator = mock(PacemakerTimeoutCalculator.class);
  private NextTxnsGenerator nextTxnsGenerator = mock(NextTxnsGenerator.class);
  private RemoteEventDispatcher<Vote> voteDispatcher = rmock(RemoteEventDispatcher.class);
  private RemoteEventDispatcher<Proposal> proposalDispatcher = rmock(RemoteEventDispatcher.class);
  private EventDispatcher<LocalTimeoutOccurrence> timeoutDispatcher = rmock(EventDispatcher.class);
  private ScheduledEventDispatcher<ScheduledLocalTimeout> timeoutSender =
      rmock(ScheduledEventDispatcher.class);
  private TimeSupplier timeSupplier = mock(TimeSupplier.class);

  private Pacemaker pacemaker;

  @Before
  public void setUp() {
    HighQC highQC = mock(HighQC.class);
    QuorumCertificate committedQc = mock(QuorumCertificate.class);
    when(committedQc.getView()).thenReturn(View.of(0));
    when(highQC.highestCommittedQC()).thenReturn(committedQc);

    ViewUpdate initialViewUpdate =
        ViewUpdate.create(View.of(0), highQC, mock(BFTNode.class), mock(BFTNode.class));

    this.pacemaker =
        new Pacemaker(
            this.self,
            this.counters,
            this.validatorSet,
            this.vertexStore,
            this.safetyRules,
            this.timeoutDispatcher,
            this.timeoutSender,
            this.timeoutCalculator,
            this.nextTxnsGenerator,
            this.proposalDispatcher,
            this.voteDispatcher,
            hasher,
            timeSupplier,
            initialViewUpdate,
            new SystemCountersImpl());
  }

  @Test
  public void when_local_timeout__then_resend_previous_vote() {
    View view = View.of(0);
    Vote lastVote = mock(Vote.class);
    Vote lastVoteWithTimeout = mock(Vote.class);
    ImmutableSet<BFTNode> validators = rmock(ImmutableSet.class);

    when(this.safetyRules.getLastVote(view)).thenReturn(Optional.of(lastVote));
    when(this.safetyRules.timeoutVote(lastVote)).thenReturn(lastVoteWithTimeout);
    when(this.validatorSet.nodes()).thenReturn(validators);

    ViewUpdate viewUpdate =
        ViewUpdate.create(View.of(0), mock(HighQC.class), mock(BFTNode.class), mock(BFTNode.class));
    this.pacemaker.processLocalTimeout(ScheduledLocalTimeout.create(viewUpdate, 0L));

    verify(this.voteDispatcher, times(1)).dispatch(eq(validators), eq(lastVoteWithTimeout));
    verifyNoMoreInteractions(this.vertexStore);
    verify(this.safetyRules, times(1)).getLastVote(view);
    verify(this.safetyRules, times(1)).timeoutVote(lastVote);
    verifyNoMoreInteractions(this.safetyRules);
  }

  @Test
  public void when_local_timeout__then_send_empty_vote_if_no_previous() {
    HighQC viewUpdateHighQc = mock(HighQC.class);
    QuorumCertificate committedQc = mock(QuorumCertificate.class);
    QuorumCertificate highestQc = mock(QuorumCertificate.class);
    when(viewUpdateHighQc.highestCommittedQC()).thenReturn(committedQc);
    when(viewUpdateHighQc.highestQC()).thenReturn(highestQc);
    BFTHeader highestQcProposed = mock(BFTHeader.class);
    HashCode highQcParentVertexId = mock(HashCode.class);
    when(highestQcProposed.getVertexId()).thenReturn(highQcParentVertexId);
    when(highestQc.getProposed()).thenReturn(highestQcProposed);
    when(committedQc.getView()).thenReturn(View.of(0));
    ViewUpdate viewUpdate =
        ViewUpdate.create(View.of(1), viewUpdateHighQc, mock(BFTNode.class), mock(BFTNode.class));
    this.pacemaker.processViewUpdate(viewUpdate);
    View view = View.of(1);
    Vote emptyVote = mock(Vote.class);
    Vote emptyVoteWithTimeout = mock(Vote.class);
    ImmutableSet<BFTNode> validators = rmock(ImmutableSet.class);
    BFTHeader bftHeader = mock(BFTHeader.class);
    HighQC highQC = mock(HighQC.class);
    BFTInsertUpdate bftInsertUpdate = mock(BFTInsertUpdate.class);
    when(bftInsertUpdate.getHeader()).thenReturn(bftHeader);
    PreparedVertex preparedVertex = mock(PreparedVertex.class);
    when(preparedVertex.getView()).thenReturn(view);
    when(preparedVertex.getLedgerHeader()).thenReturn(mock(LedgerHeader.class));
    VerifiedVertexStoreState vertexStoreState = mock(VerifiedVertexStoreState.class);
    when(vertexStoreState.getHighQC()).thenReturn(highQC);
    when(bftInsertUpdate.getInserted()).thenReturn(preparedVertex);
    when(bftInsertUpdate.getVertexStoreState()).thenReturn(vertexStoreState);
    var node = BFTNode.random();
    when(preparedVertex.getId())
        .thenReturn(hasher.hash(UnverifiedVertex.createTimeout(highestQc, view, node)));

    when(this.safetyRules.getLastVote(view)).thenReturn(Optional.empty());
    when(this.safetyRules.createVote(any(), any(), anyLong(), any())).thenReturn(emptyVote);
    when(this.safetyRules.timeoutVote(emptyVote)).thenReturn(emptyVoteWithTimeout);
    when(this.validatorSet.nodes()).thenReturn(validators);

    when(this.vertexStore.getPreparedVertex(any())).thenReturn(Optional.empty());

    this.pacemaker.processLocalTimeout(
        ScheduledLocalTimeout.create(
            ViewUpdate.create(View.of(1), mock(HighQC.class), node, BFTNode.random()), 0L));

    this.pacemaker.processBFTUpdate(bftInsertUpdate);

    verify(this.voteDispatcher, times(1)).dispatch(eq(validators), eq(emptyVoteWithTimeout));
    verify(this.safetyRules, times(1)).getLastVote(view);
    verify(this.safetyRules, times(1)).createVote(any(), any(), anyLong(), any());
    verify(this.safetyRules, times(1)).timeoutVote(emptyVote);
    verifyNoMoreInteractions(this.safetyRules);

    verify(this.vertexStore, times(1)).getPreparedVertex(any());

    ArgumentCaptor<VerifiedVertex> insertVertexCaptor =
        ArgumentCaptor.forClass(VerifiedVertex.class);
    verify(this.vertexStore, times(1)).insertVertex(insertVertexCaptor.capture());
    assertEquals(insertVertexCaptor.getValue().getParentId(), highQcParentVertexId);

    verifyNoMoreInteractions(this.vertexStore);
  }

  @Test
  public void when_local_timeout_for_non_current_view__then_ignored() {
    this.pacemaker.processLocalTimeout(
        ScheduledLocalTimeout.create(
            ViewUpdate.create(
                View.of(1), mock(HighQC.class), mock(BFTNode.class), mock(BFTNode.class)),
            0L));
    verifyNoMoreInteractions(this.safetyRules);
  }
}
