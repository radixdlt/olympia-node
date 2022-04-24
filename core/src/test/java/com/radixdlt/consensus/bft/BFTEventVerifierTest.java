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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.radixdlt.consensus.BFTEventProcessor;
import com.radixdlt.consensus.HashVerifier;
import com.radixdlt.consensus.Proposal;
import com.radixdlt.consensus.Vote;
import com.radixdlt.consensus.liveness.ScheduledLocalTimeout;
import com.radixdlt.crypto.ECDSASignature;
import com.radixdlt.crypto.Hasher;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;

public class BFTEventVerifierTest {

  private BFTValidatorSet validatorSet;
  private BFTEventProcessor forwardTo;
  private Hasher hasher;
  private HashVerifier verifier;
  private BFTEventVerifier eventVerifier;

  @Before
  public void setup() {
    this.validatorSet = mock(BFTValidatorSet.class);
    this.forwardTo = mock(BFTEventProcessor.class);
    this.hasher = mock(Hasher.class);
    this.verifier = mock(HashVerifier.class);
    this.eventVerifier = new BFTEventVerifier(validatorSet, forwardTo, hasher, verifier);
  }

  @Test
  public void when_start__then_should_be_forwarded() {
    eventVerifier.start();
    verify(forwardTo, times(1)).start();
  }

  @Test
  public void when_process_local_timeout__then_should_be_forwarded() {
    ScheduledLocalTimeout timeout = mock(ScheduledLocalTimeout.class);
    eventVerifier.processLocalTimeout(timeout);
    verify(forwardTo, times(1)).processLocalTimeout(eq(timeout));
  }

  @Test
  public void when_process_local_sync__then_should_be_forwarded() {
    BFTInsertUpdate update = mock(BFTInsertUpdate.class);
    eventVerifier.processBFTUpdate(update);
    verify(forwardTo, times(1)).processBFTUpdate(update);
  }

  @Test
  public void when_process_correct_proposal_then_should_be_forwarded() {
    Proposal proposal = mock(Proposal.class);
    BFTNode author = mock(BFTNode.class);
    when(proposal.getAuthor()).thenReturn(author);
    when(proposal.getSignature()).thenReturn(mock(ECDSASignature.class));
    when(validatorSet.containsNode(eq(author))).thenReturn(true);
    when(verifier.verify(any(), any(), any())).thenReturn(true);
    eventVerifier.processProposal(proposal);
    verify(forwardTo, times(1)).processProposal(eq(proposal));
  }

  @Test
  public void when_process_bad_author_proposal_then_should_not_be_forwarded() {
    Proposal proposal = mock(Proposal.class);
    BFTNode author = mock(BFTNode.class);
    when(proposal.getAuthor()).thenReturn(author);
    when(proposal.getSignature()).thenReturn(mock(ECDSASignature.class));
    when(validatorSet.containsNode(eq(author))).thenReturn(false);
    when(verifier.verify(any(), any(), any())).thenReturn(true);
    eventVerifier.processProposal(proposal);
    verify(forwardTo, never()).processProposal(any());
  }

  @Test
  public void when_process_bad_signature_proposal_then_should_not_be_forwarded() {
    Proposal proposal = mock(Proposal.class);
    BFTNode author = mock(BFTNode.class);
    when(proposal.getAuthor()).thenReturn(author);
    when(proposal.getSignature()).thenReturn(mock(ECDSASignature.class));
    when(validatorSet.containsNode(eq(author))).thenReturn(true);
    when(verifier.verify(any(), any(), any())).thenReturn(false);
    eventVerifier.processProposal(proposal);
    verify(forwardTo, never()).processProposal(any());
  }

  @Test
  public void when_process_correct_vote_then_should_be_forwarded() {
    Vote vote = mock(Vote.class);
    when(vote.getView()).thenReturn(View.of(1));
    when(vote.getEpoch()).thenReturn(0L);
    BFTNode author = mock(BFTNode.class);
    when(vote.getAuthor()).thenReturn(author);
    ECDSASignature voteSignature = mock(ECDSASignature.class);
    ECDSASignature timeoutSignature = mock(ECDSASignature.class);
    when(vote.getSignature()).thenReturn(voteSignature);
    when(vote.getTimeoutSignature()).thenReturn(Optional.of(timeoutSignature));
    when(validatorSet.containsNode(eq(author))).thenReturn(true);
    when(verifier.verify(any(), any(), eq(voteSignature))).thenReturn(true);
    when(verifier.verify(any(), any(), eq(timeoutSignature))).thenReturn(true);
    eventVerifier.processVote(vote);
    verify(forwardTo, times(1)).processVote(eq(vote));
  }

  @Test
  public void when_process_bad_author_vote_then_should_not_be_forwarded() {
    Vote vote = mock(Vote.class);
    BFTNode author = mock(BFTNode.class);
    when(vote.getAuthor()).thenReturn(author);
    when(vote.getSignature()).thenReturn(mock(ECDSASignature.class));
    when(validatorSet.containsNode(eq(author))).thenReturn(false);
    when(verifier.verify(any(), any(), any())).thenReturn(true);
    eventVerifier.processVote(vote);
    verify(forwardTo, never()).processVote(any());
  }

  @Test
  public void when_process_bad_signature_vote_then_should_not_be_forwarded() {
    Vote vote = mock(Vote.class);
    BFTNode author = mock(BFTNode.class);
    when(vote.getAuthor()).thenReturn(author);
    when(vote.getSignature()).thenReturn(mock(ECDSASignature.class));
    when(validatorSet.containsNode(eq(author))).thenReturn(true);
    when(verifier.verify(any(), any(), any())).thenReturn(false);
    eventVerifier.processVote(vote);
    verify(forwardTo, never()).processVote(any());
  }

  @Test
  public void when_process_bad_timeout_signature_vote_then_should_not_be_forwarded() {
    Vote vote = mock(Vote.class);
    when(vote.getView()).thenReturn(View.of(1));
    when(vote.getEpoch()).thenReturn(0L);
    BFTNode author = mock(BFTNode.class);
    when(vote.getAuthor()).thenReturn(author);
    ECDSASignature voteSignature = mock(ECDSASignature.class);
    ECDSASignature timeoutSignature = mock(ECDSASignature.class);
    when(vote.getSignature()).thenReturn(voteSignature);
    when(vote.getTimeoutSignature()).thenReturn(Optional.of(timeoutSignature));
    when(validatorSet.containsNode(eq(author))).thenReturn(true);
    when(verifier.verify(any(), any(), eq(voteSignature))).thenReturn(true);
    when(verifier.verify(any(), any(), eq(timeoutSignature))).thenReturn(false);
    eventVerifier.processVote(vote);
    verify(forwardTo, never()).processVote(any());
  }
}
