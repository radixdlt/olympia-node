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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableSet;
import com.radixdlt.consensus.BFTEventProcessor;
import com.radixdlt.consensus.DefaultHasher;
import com.radixdlt.consensus.HashSigner;
import com.radixdlt.consensus.HashVerifier;
import com.radixdlt.consensus.Hasher;
import com.radixdlt.consensus.Proposal;
import com.radixdlt.consensus.Vertex;
import com.radixdlt.consensus.bft.BFTEventReducer.BFTEventSender;
import com.radixdlt.consensus.bft.BFTEventReducer.BFTInfoSender;
import com.radixdlt.consensus.bft.BFTEventReducer.EndOfEpochSender;
import com.radixdlt.consensus.liveness.Pacemaker;
import com.radixdlt.consensus.liveness.ProposerElection;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.crypto.ECDSASignature;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.mempool.Mempool;
import com.radixdlt.utils.UInt256;
import org.junit.Before;
import org.junit.Test;

public class BFTBuilderTest {
	private Mempool mempool;
	private BFTEventSender eventSender;
	private EndOfEpochSender endOfEpochSender;
	private SystemCounters counters;
	private BFTInfoSender infoSender;
	private BFTValidatorSet validatorSet;
	private ProposerElection proposerElection;
	private Hasher hasher = new DefaultHasher();
	private HashSigner signer;
	private HashVerifier verifier = ECPublicKey::verify;
	private Pacemaker pacemaker;
	private VertexStore vertexStore;
	private BFTNode self;

	@Before
	public void setup() {
		mempool = mock(Mempool.class);
		eventSender = mock(BFTEventSender.class);
		endOfEpochSender = mock(EndOfEpochSender.class);
		counters = mock(SystemCounters.class);
		validatorSet = mock(BFTValidatorSet.class);
		proposerElection = mock(ProposerElection.class);
		hasher = mock(Hasher.class);
		signer = mock(HashSigner.class);
		verifier = mock(HashVerifier.class);
		pacemaker = mock(Pacemaker.class);
		vertexStore = mock(VertexStore.class);
		self = mock(BFTNode.class);
		infoSender = mock(BFTInfoSender.class);
	}

	@Test
	public void when_build__then_should_create_event_processor_which_verifies_proposal() {
		when(validatorSet.containsNode(any())).thenReturn(true);
		when(validatorSet.getValidators()).thenReturn(ImmutableSet.of(BFTValidator.from(self, UInt256.ONE)));
		when(verifier.verify(any(), any(), any())).thenReturn(false);

		BFTEventProcessor processor = BFTBuilder.create()
			.mempool(mempool)
			.eventSender(eventSender)
			.endOfEpochSender(endOfEpochSender)
			.counters(counters)
			.infoSender(infoSender)
			.timeSupplier(System::currentTimeMillis)
			.validatorSet(validatorSet)
			.proposerElection(proposerElection)
			.hasher(hasher)
			.signer(signer)
			.verifier(verifier)
			.pacemaker(pacemaker)
			.vertexStore(vertexStore)
			.self(self)
			.build();

		Proposal proposal = mock(Proposal.class);
		when(proposal.getAuthor()).thenReturn(self);
		when(proposal.getSignature()).thenReturn(mock(ECDSASignature.class));
		processor.processProposal(proposal);

		verify(verifier, times(1)).verify(any(), any(), any());
	}

	@Test
	public void when_build_with_no_verification__then_should_not_call_verifier() {
		when(validatorSet.containsNode(any())).thenReturn(true);
		when(validatorSet.getValidators()).thenReturn(ImmutableSet.of(BFTValidator.from(self, UInt256.ONE)));
		when(verifier.verify(any(), any(), any())).thenReturn(false);

		BFTEventProcessor processor = BFTBuilder.create()
			.mempool(mempool)
			.eventSender(eventSender)
			.endOfEpochSender(endOfEpochSender)
			.counters(counters)
			.infoSender(infoSender)
			.timeSupplier(System::currentTimeMillis)
			.validatorSet(validatorSet)
			.proposerElection(proposerElection)
			.hasher(hasher)
			.signer(signer)
			.verifier(verifier)
			.pacemaker(pacemaker)
			.vertexStore(vertexStore)
			.self(self)
			.verifyAuthors(false)
			.build();

		Proposal proposal = mock(Proposal.class);
		when(proposal.getAuthor()).thenReturn(self);
		when(proposal.getSignature()).thenReturn(mock(ECDSASignature.class));
		Vertex vertex = mock(Vertex.class);
		when(vertex.getView()).thenReturn(View.of(1));
		when(proposal.getVertex()).thenReturn(vertex);
		when(pacemaker.getCurrentView()).thenReturn(View.of(2));
		processor.processProposal(proposal);

		verify(verifier, never()).verify(any(), any(), any());
	}
}