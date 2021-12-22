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

package com.radixdlt.consensus.bft;

import static com.radixdlt.utils.TypedMocks.rmock;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.radixdlt.consensus.BFTHeader;
import com.radixdlt.consensus.HighQC;
import com.radixdlt.consensus.PendingVotes;
import com.radixdlt.consensus.QuorumCertificate;
import com.radixdlt.consensus.Vote;
import com.radixdlt.consensus.liveness.Pacemaker;
import com.radixdlt.consensus.liveness.ScheduledLocalTimeout;
import com.radixdlt.consensus.safety.SafetyRules;
import com.radixdlt.crypto.Hasher;
import com.radixdlt.environment.EventDispatcher;
import com.radixdlt.environment.RemoteEventDispatcher;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;

public class BFTEventReducerTest {

  private BFTNode self = mock(BFTNode.class);
  private Hasher hasher = mock(Hasher.class);
  private RemoteEventDispatcher<Vote> voteDispatcher = rmock(RemoteEventDispatcher.class);
  private PendingVotes pendingVotes = mock(PendingVotes.class);
  private BFTValidatorSet validatorSet = mock(BFTValidatorSet.class);
  private VertexStore vertexStore = mock(VertexStore.class);
  private SafetyRules safetyRules = mock(SafetyRules.class);
  private Pacemaker pacemaker = mock(Pacemaker.class);
  private EventDispatcher<ViewQuorumReached> viewQuorumReachedEventDispatcher =
      rmock(EventDispatcher.class);
  private EventDispatcher<NoVote> noVoteEventDispatcher = rmock(EventDispatcher.class);

  private BFTEventReducer bftEventReducer;

  @Before
  public void setUp() {
    this.bftEventReducer =
        new BFTEventReducer(
            this.self,
            this.pacemaker,
            this.vertexStore,
            this.viewQuorumReachedEventDispatcher,
            this.noVoteEventDispatcher,
            this.voteDispatcher,
            this.hasher,
            this.safetyRules,
            this.validatorSet,
            this.pendingVotes,
            mock(ViewUpdate.class));
  }

  @Test
  public void when_bft_update_for_previous_view__then_ignore() {
    BFTInsertUpdate update = mock(BFTInsertUpdate.class);
    BFTHeader header = mock(BFTHeader.class);
    this.bftEventReducer.processViewUpdate(
        ViewUpdate.create(View.of(3), mock(HighQC.class), mock(BFTNode.class), this.self));
    verify(this.pacemaker, times(1)).processViewUpdate(any());

    when(update.getHeader()).thenReturn(header);
    when(header.getView()).thenReturn(View.of(2));
    this.bftEventReducer.processBFTUpdate(update);

    verifyNoMoreInteractions(this.pacemaker);
  }

  @Test
  public void when_view_is_timed_out__then_dont_vote() {
    BFTInsertUpdate bftUpdate = mock(BFTInsertUpdate.class);
    BFTHeader header = mock(BFTHeader.class);
    when(bftUpdate.getHeader()).thenReturn(header);
    when(header.getView()).thenReturn(View.of(3));

    ViewUpdate viewUpdate =
        ViewUpdate.create(View.of(3), mock(HighQC.class), mock(BFTNode.class), this.self);
    this.bftEventReducer.processViewUpdate(viewUpdate);
    verify(this.pacemaker, times(1)).processViewUpdate(any());

    this.bftEventReducer.processLocalTimeout(ScheduledLocalTimeout.create(viewUpdate, 1000));
    verify(this.pacemaker, times(1)).processLocalTimeout(any());

    this.bftEventReducer.processBFTUpdate(bftUpdate);

    verifyNoMoreInteractions(this.voteDispatcher);
    verifyNoMoreInteractions(this.noVoteEventDispatcher);
  }

  @Test
  public void when_previous_vote_exists_for_this_view__then_dont_vote() {
    BFTInsertUpdate bftUpdate = mock(BFTInsertUpdate.class);
    BFTHeader header = mock(BFTHeader.class);
    when(bftUpdate.getHeader()).thenReturn(header);
    when(header.getView()).thenReturn(View.of(3));

    ViewUpdate viewUpdate =
        ViewUpdate.create(View.of(3), mock(HighQC.class), mock(BFTNode.class), this.self);
    this.bftEventReducer.processViewUpdate(viewUpdate);
    verify(this.pacemaker, times(1)).processViewUpdate(any());

    when(safetyRules.getLastVote(View.of(3))).thenReturn(Optional.of(mock(Vote.class)));
    this.bftEventReducer.processBFTUpdate(bftUpdate);

    verifyNoMoreInteractions(this.voteDispatcher);
    verifyNoMoreInteractions(this.noVoteEventDispatcher);
  }

  @Test
  public void when_process_vote_with_quorum_wrong_view__then_ignored() {
    Vote vote = mock(Vote.class);
    when(vote.getView()).thenReturn(View.of(1));
    this.bftEventReducer.processViewUpdate(
        ViewUpdate.create(View.of(3), mock(HighQC.class), mock(BFTNode.class), this.self));
    this.bftEventReducer.processVote(vote);
    verifyNoMoreInteractions(this.pendingVotes);
  }

  @Test
  public void when_process_vote_with_quorum__then_processed() {
    BFTNode author = mock(BFTNode.class);
    Vote vote = mock(Vote.class);
    when(vote.getAuthor()).thenReturn(author);

    QuorumCertificate qc = mock(QuorumCertificate.class);
    HighQC highQc = mock(HighQC.class);
    QuorumCertificate highestCommittedQc = mock(QuorumCertificate.class);
    when(highQc.highestCommittedQC()).thenReturn(highestCommittedQc);
    when(vote.getView()).thenReturn(View.of(1));

    when(this.pendingVotes.insertVote(any(), any())).thenReturn(VoteProcessingResult.qcQuorum(qc));
    when(this.vertexStore.highQC()).thenReturn(highQc);

    // Move to view 1
    this.bftEventReducer.processViewUpdate(
        ViewUpdate.create(View.of(1), highQc, mock(BFTNode.class), this.self));

    this.bftEventReducer.processVote(vote);

    verify(this.viewQuorumReachedEventDispatcher, times(1)).dispatch(any());
    verify(this.pendingVotes, times(1)).insertVote(eq(vote), any());
    verifyNoMoreInteractions(this.pendingVotes);
  }
}
