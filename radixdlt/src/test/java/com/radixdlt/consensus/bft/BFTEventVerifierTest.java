/*
 * (C) Copyright 2020 Radix DLT Ltd
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

package com.radixdlt.consensus.bft;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.powermock.api.mockito.PowerMockito.when;

import com.radixdlt.consensus.BFTEventProcessor;
import com.radixdlt.consensus.HashVerifier;
import com.radixdlt.consensus.Hasher;
import com.radixdlt.consensus.NewView;
import com.radixdlt.consensus.Proposal;
import com.radixdlt.consensus.Vote;
import com.radixdlt.crypto.ECDSASignature;
import com.radixdlt.crypto.Hash;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;

public class BFTEventVerifierTest {

	private BFTNode self;
	private BFTValidatorSet validatorSet;
	private BFTEventProcessor forwardTo;
	private Hasher hasher;
	private HashVerifier verifier;
	private BFTEventVerifier eventVerifier;

	@Before
	public void setup() {
		this.self = mock(BFTNode.class);
		this.validatorSet = mock(BFTValidatorSet.class);
		this.forwardTo = mock(BFTEventProcessor.class);
		this.hasher = mock(Hasher.class);
		this.verifier = mock(HashVerifier.class);
		this.eventVerifier = new BFTEventVerifier(self, validatorSet, forwardTo, hasher, verifier);
	}

	@Test
	public void when_start__then_should_be_forwarded() {
		eventVerifier.start();
		verify(forwardTo, times(1)).start();
	}

	@Test
	public void when_process_local_timeout__then_should_be_forwarded() {
		View view = mock(View.class);
		eventVerifier.processLocalTimeout(view);
		verify(forwardTo, times(1)).processLocalTimeout(eq(view));
	}

	@Test
	public void when_process_local_sync__then_should_be_forwarded() {
		Hash sync = mock(Hash.class);
		eventVerifier.processLocalSync(sync);
		verify(forwardTo, times(1)).processLocalSync(sync);
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
		verify(forwardTo, never()).processProposal(eq(proposal));
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
		verify(forwardTo, never()).processProposal(eq(proposal));
	}

	@Test
	public void when_process_correct_newview_then_should_be_forwarded() {
		NewView newView = mock(NewView.class);
		BFTNode author = mock(BFTNode.class);
		when(newView.getAuthor()).thenReturn(author);
		when(newView.getView()).thenReturn(View.of(1));
		when(newView.getSignature()).thenReturn(Optional.of(mock(ECDSASignature.class)));
		when(validatorSet.containsNode(eq(author))).thenReturn(true);
		when(verifier.verify(any(), any(), any())).thenReturn(true);
		eventVerifier.processNewView(newView);
		verify(forwardTo, times(1)).processNewView(eq(newView));
	}

	@Test
	public void when_process_bad_author_newview_then_should_not_be_forwarded() {
		NewView newView = mock(NewView.class);
		BFTNode author = mock(BFTNode.class);
		when(newView.getAuthor()).thenReturn(author);
		when(newView.getView()).thenReturn(View.of(1));
		when(newView.getSignature()).thenReturn(Optional.of(mock(ECDSASignature.class)));
		when(validatorSet.containsNode(eq(author))).thenReturn(false);
		when(verifier.verify(any(), any(), any())).thenReturn(true);
		eventVerifier.processNewView(newView);
		verify(forwardTo, never()).processNewView(eq(newView));
	}

	@Test
	public void when_process_bad_signature_newview_then_should_not_be_forwarded() {
		NewView newView = mock(NewView.class);
		BFTNode author = mock(BFTNode.class);
		when(newView.getAuthor()).thenReturn(author);
		when(newView.getView()).thenReturn(View.of(1));
		when(newView.getSignature()).thenReturn(Optional.of(mock(ECDSASignature.class)));
		when(validatorSet.containsNode(eq(author))).thenReturn(true);
		when(verifier.verify(any(), any(), any())).thenReturn(false);
		eventVerifier.processNewView(newView);
		verify(forwardTo, never()).processNewView(eq(newView));
	}


	@Test
	public void when_process_correct_vote_then_should_be_forwarded() {
		Vote vote = mock(Vote.class);
		BFTNode author = mock(BFTNode.class);
		when(vote.getAuthor()).thenReturn(author);
		when(vote.getSignature()).thenReturn(Optional.of(mock(ECDSASignature.class)));
		when(validatorSet.containsNode(eq(author))).thenReturn(true);
		when(verifier.verify(any(), any(), any())).thenReturn(true);
		eventVerifier.processVote(vote);
		verify(forwardTo, times(1)).processVote(eq(vote));
	}

	@Test
	public void when_process_bad_author_vote_then_should_not_be_forwarded() {
		Vote vote = mock(Vote.class);
		BFTNode author = mock(BFTNode.class);
		when(vote.getAuthor()).thenReturn(author);
		when(vote.getSignature()).thenReturn(Optional.of(mock(ECDSASignature.class)));
		when(validatorSet.containsNode(eq(author))).thenReturn(false);
		when(verifier.verify(any(), any(), any())).thenReturn(true);
		eventVerifier.processVote(vote);
		verify(forwardTo, never()).processVote(eq(vote));
	}

	@Test
	public void when_process_bad_signature_vote_then_should_not_be_forwarded() {
		Vote vote = mock(Vote.class);
		BFTNode author = mock(BFTNode.class);
		when(vote.getAuthor()).thenReturn(author);
		when(vote.getSignature()).thenReturn(Optional.of(mock(ECDSASignature.class)));
		when(validatorSet.containsNode(eq(author))).thenReturn(true);
		when(verifier.verify(any(), any(), any())).thenReturn(false);
		eventVerifier.processVote(vote);
		verify(forwardTo, never()).processVote(eq(vote));
	}
}