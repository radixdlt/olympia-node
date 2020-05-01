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

package com.radixdlt.consensus;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableSet;
import com.radixdlt.consensus.liveness.Pacemaker;
import com.radixdlt.consensus.liveness.ProposerElection;
import com.radixdlt.consensus.validators.Validator;
import com.radixdlt.consensus.validators.ValidatorSet;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.crypto.ECKeyPair;
import org.junit.Before;
import org.junit.Test;

public class BFTEventPreprocessorTest {
	private static final ECKeyPair SELF_KEY = ECKeyPair.generateNew();
	private BFTEventPreprocessor preprocessor;
	private ProposerElection proposerElection;
	private Pacemaker pacemaker;
	private VertexStore vertexStore;
	private SystemCounters counters;
	private BFTEventProcessor forwardTo;

	@Before
	public void setUp() {
		this.pacemaker = mock(Pacemaker.class);
		this.vertexStore = mock(VertexStore.class);
		this.proposerElection = mock(ProposerElection.class);
		this.counters = mock(SystemCounters.class);
		this.forwardTo = mock(BFTEventProcessor.class);

		Validator validator = mock(Validator.class);
		when(validator.nodeKey()).thenReturn(SELF_KEY.getPublicKey());
		ValidatorSet validatorSet = mock(ValidatorSet.class);
		when(validatorSet.getValidators()).thenReturn(ImmutableSet.of(validator));

		this.preprocessor = new BFTEventPreprocessor(
			SELF_KEY.getPublicKey(),
			forwardTo,
			pacemaker,
			vertexStore,
			proposerElection,
			validatorSet,
			counters
		);
	}

	@Test
	public void when_process_irrelevant_new_view__then_no_event_occurs() {
		NewView newView = mock(NewView.class);
		when(newView.getAuthor()).thenReturn(SELF_KEY.getPublicKey());
		when(newView.getView()).thenReturn(View.of(0L));
		when(pacemaker.getCurrentView()).thenReturn(View.of(1L));
		preprocessor.processNewView(newView);
		verify(forwardTo, never()).processNewView(any());
	}

	@Test
	public void when_processing_old_proposal__then_no_vertex_is_inserted() {
		when(pacemaker.getCurrentView()).thenReturn(View.of(10));
		Vertex vertex = mock(Vertex.class);
		when(vertex.getView()).thenReturn(View.of(9));
		Proposal proposal = mock(Proposal.class);
		when(proposal.getAuthor()).thenReturn(SELF_KEY.getPublicKey());
		when(proposal.getVertex()).thenReturn(vertex);
		preprocessor.processProposal(proposal);
		verify(forwardTo, never()).processProposal(any());
	}

	@Test
	public void when_processing_new_view_as_not_proposer__then_new_view_is_not_emitted() {
		NewView newView = mock(NewView.class);
		when(newView.getAuthor()).thenReturn(SELF_KEY.getPublicKey());
		when(newView.getView()).thenReturn(View.of(0L));
		when(pacemaker.getCurrentView()).thenReturn(View.of(0L));
		preprocessor.processNewView(newView);
		verify(forwardTo, never()).processNewView(any());
	}
}