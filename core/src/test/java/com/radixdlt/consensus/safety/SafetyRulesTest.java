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

package com.radixdlt.consensus.safety;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.hash.HashCode;
import com.radixdlt.consensus.BFTHeader;
import com.radixdlt.consensus.HashSigner;
import com.radixdlt.consensus.HighQC;
import com.radixdlt.consensus.Vote;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.VerifiedVertex;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.consensus.safety.SafetyState.Builder;
import com.radixdlt.crypto.ECDSASignature;
import com.radixdlt.crypto.HashUtils;
import com.radixdlt.crypto.Hasher;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;

/**
 * This tests that the {@link SafetyRules} implementation obeys HotStuff's safety and commit rules.
 */
public class SafetyRulesTest {
  private SafetyState safetyState;
  private SafetyRules safetyRules;

  @Before
  public void setup() {
    this.safetyState = mock(SafetyState.class);
    Hasher hasher = mock(Hasher.class);
    when(hasher.hash(any())).thenReturn(HashUtils.random256());
    when(hasher.hashBytes(any())).thenReturn(HashUtils.random256());
    HashSigner hashSigner = mock(HashSigner.class);
    when(hashSigner.sign(any(HashCode.class))).thenReturn(ECDSASignature.zeroSignature());
    this.safetyRules =
        new SafetyRules(
            mock(BFTNode.class),
            safetyState,
            mock(PersistentSafetyStateStore.class),
            hasher,
            hashSigner);
  }

  @Test
  public void when_vote_on_same_view__then_exception_is_thrown() {
    View view = mock(View.class);
    when(view.lte(view)).thenReturn(true);
    when(safetyState.getLastVotedView()).thenReturn(view);
    VerifiedVertex vertex = mock(VerifiedVertex.class);
    when(vertex.getView()).thenReturn(view);

    assertThat(this.safetyRules.voteFor(vertex, mock(BFTHeader.class), 0L, mock(HighQC.class)))
        .isEmpty();
  }

  @Test
  public void when_vote_with_qc_on_different_locked_view__then_exception_is_thrown() {
    Hasher hasher = mock(Hasher.class);
    when(hasher.hash(any())).thenReturn(mock(HashCode.class));
    HashSigner hashSigner = mock(HashSigner.class);
    when(hashSigner.sign(any(HashCode.class))).thenReturn(ECDSASignature.zeroSignature());

    Vote lastVote = mock(Vote.class);
    when(lastVote.getView()).thenReturn(View.of(1));

    SafetyRules safetyRules =
        new SafetyRules(
            BFTNode.random(),
            new SafetyState(View.of(2), Optional.of(lastVote)),
            mock(PersistentSafetyStateStore.class),
            hasher,
            hashSigner);

    VerifiedVertex vertex = mock(VerifiedVertex.class);
    when(vertex.getView()).thenReturn(View.of(3));
    BFTHeader parent = mock(BFTHeader.class);
    when(parent.getView()).thenReturn(View.of(0));
    when(vertex.getParentHeader()).thenReturn(parent);

    assertThat(safetyRules.voteFor(vertex, mock(BFTHeader.class), 0L, mock(HighQC.class)))
        .isEmpty();
  }

  @Test
  public void when_vote_on_proposal_after_genesis__then_returned_vote_has_no_commit() {
    when(safetyState.getLastVotedView()).thenReturn(View.of(0));
    when(safetyState.getLockedView()).thenReturn(View.of(0));
    when(safetyState.toBuilder()).thenReturn(mock(Builder.class));
    VerifiedVertex vertex = mock(VerifiedVertex.class);
    when(vertex.hasDirectParent()).thenReturn(true);
    when(vertex.touchesGenesis()).thenReturn(true);
    when(vertex.parentHasDirectParent()).thenReturn(true);
    when(vertex.getView()).thenReturn(View.of(1));
    BFTHeader parent = mock(BFTHeader.class);
    when(parent.getView()).thenReturn(View.of(0));
    when(vertex.getParentHeader()).thenReturn(parent);
    BFTHeader grandParent = mock(BFTHeader.class);
    when(grandParent.getView()).thenReturn(mock(View.class));
    when(vertex.getGrandParentHeader()).thenReturn(grandParent);
    BFTHeader header = mock(BFTHeader.class);
    Optional<Vote> voteMaybe = safetyRules.voteFor(vertex, header, 1L, mock(HighQC.class));
    assertThat(voteMaybe).isNotEmpty();
    Vote vote = voteMaybe.get();
    assertThat(vote.getVoteData().getProposed()).isEqualTo(header);
    assertThat(vote.getVoteData().getParent()).isEqualTo(parent);
    assertThat(vote.getVoteData().getCommitted()).isEmpty();
  }

  @Test
  public void when_vote_on_proposal_two_after_genesis__then_returned_vote_has_no_commit() {
    when(safetyState.getLastVotedView()).thenReturn(View.of(1));
    when(safetyState.getLockedView()).thenReturn(View.of(0));
    when(safetyState.toBuilder()).thenReturn(mock(Builder.class));
    VerifiedVertex proposal = mock(VerifiedVertex.class);
    when(proposal.touchesGenesis()).thenReturn(true);
    when(proposal.hasDirectParent()).thenReturn(true);
    when(proposal.parentHasDirectParent()).thenReturn(true);
    BFTHeader parent = mock(BFTHeader.class);
    when(parent.getView()).thenReturn(View.of(1));
    when(proposal.getParentHeader()).thenReturn(parent);
    when(proposal.getView()).thenReturn(View.of(2));
    BFTHeader grandParent = mock(BFTHeader.class);
    when(grandParent.getView()).thenReturn(mock(View.class));
    when(proposal.getGrandParentHeader()).thenReturn(grandParent);
    Optional<Vote> voteMaybe =
        safetyRules.voteFor(proposal, mock(BFTHeader.class), 1L, mock(HighQC.class));
    assertThat(voteMaybe).isNotEmpty();
    Vote vote = voteMaybe.get();
    assertThat(vote.getVoteData().getCommitted()).isEmpty();
  }

  @Test
  public void when_vote_on_proposal_three_after_genesis__then_returned_vote_has_commit() {
    when(safetyState.getLastVotedView()).thenReturn(View.of(1));
    when(safetyState.getLockedView()).thenReturn(View.of(0));
    when(safetyState.toBuilder()).thenReturn(mock(Builder.class));

    VerifiedVertex proposal = mock(VerifiedVertex.class);
    when(proposal.touchesGenesis()).thenReturn(false);
    when(proposal.hasDirectParent()).thenReturn(true);
    when(proposal.parentHasDirectParent()).thenReturn(true);
    BFTHeader grandparentHeader = mock(BFTHeader.class);
    when(grandparentHeader.getView()).thenReturn(mock(View.class));
    when(proposal.getGrandParentHeader()).thenReturn(grandparentHeader);
    BFTHeader parent = mock(BFTHeader.class);
    when(parent.getView()).thenReturn(View.of(2));
    when(proposal.getParentHeader()).thenReturn(parent);
    when(proposal.getView()).thenReturn(View.of(3));

    Optional<Vote> voteMaybe =
        safetyRules.voteFor(proposal, mock(BFTHeader.class), 1L, mock(HighQC.class));
    assertThat(voteMaybe).isNotEmpty();
    Vote vote = voteMaybe.get();
    assertThat(vote.getVoteData().getCommitted()).hasValue(grandparentHeader);
  }

  @Test
  public void
      when_vote_on_proposal_three_after_genesis_with_skip__then_returned_vote_has_no_commit() {
    when(safetyState.getLastVotedView()).thenReturn(View.of(1));
    when(safetyState.getLockedView()).thenReturn(View.of(0));
    when(safetyState.toBuilder()).thenReturn(mock(Builder.class));

    VerifiedVertex proposal = mock(VerifiedVertex.class);
    when(proposal.touchesGenesis()).thenReturn(false);
    when(proposal.hasDirectParent()).thenReturn(false);
    when(proposal.parentHasDirectParent()).thenReturn(true);
    BFTHeader parent = mock(BFTHeader.class);
    when(parent.getView()).thenReturn(View.of(2));
    when(proposal.getParentHeader()).thenReturn(parent);
    when(proposal.getView()).thenReturn(View.of(4));
    BFTHeader grandParent = mock(BFTHeader.class);
    when(grandParent.getView()).thenReturn(mock(View.class));
    when(proposal.getGrandParentHeader()).thenReturn(grandParent);

    Optional<Vote> voteMaybe =
        safetyRules.voteFor(proposal, mock(BFTHeader.class), 1L, mock(HighQC.class));
    assertThat(voteMaybe).isNotEmpty();
    Vote vote = voteMaybe.get();
    assertThat(vote.getVoteData().getCommitted()).isEmpty();
  }

  @Test
  public void when_timeout_already_timed_out_vote_than_the_same_vote_is_returned() {
    Vote vote = mock(Vote.class);
    when(vote.isTimeout()).thenReturn(true);
    assertEquals(vote, safetyRules.timeoutVote(vote));
  }

  @Test
  public void when_timeout_a_vote_than_it_has_a_timeout_signature() {
    Vote vote = mock(Vote.class);
    Vote voteWithTimeout = mock(Vote.class);
    when(vote.getView()).thenReturn(View.of(1));
    when(vote.getEpoch()).thenReturn(1L);
    when(vote.withTimeoutSignature(any())).thenReturn(voteWithTimeout);
    when(vote.isTimeout()).thenReturn(false);

    Builder builder = mock(Builder.class);
    when(builder.lastVote(any())).thenReturn(builder);
    when(builder.build()).thenReturn(this.safetyState);
    when(safetyState.toBuilder()).thenReturn(builder);

    Vote resultVote = safetyRules.timeoutVote(vote);
    verify(vote, times(1)).withTimeoutSignature(any());
    assertEquals(voteWithTimeout, resultVote);
  }
}
