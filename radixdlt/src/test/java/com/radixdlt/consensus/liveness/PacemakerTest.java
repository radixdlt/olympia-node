/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 */

package com.radixdlt.consensus.liveness;

import com.google.common.hash.HashCode;
import com.radixdlt.environment.EventDispatcher;
import java.util.Optional;

import com.radixdlt.consensus.Sha256Hasher;
import com.radixdlt.crypto.Hasher;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import com.radixdlt.consensus.BFTHeader;
import com.radixdlt.consensus.HashSigner;
import com.radixdlt.consensus.LedgerHeader;
import com.radixdlt.consensus.QuorumCertificate;
import com.radixdlt.consensus.ViewTimeout;
import com.radixdlt.consensus.HighQC;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.BFTValidatorSet;
import com.radixdlt.consensus.bft.VertexStore;
import com.radixdlt.consensus.safety.SafetyRules;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.crypto.ECDSASignature;

import static com.radixdlt.utils.TypedMocks.rmock;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

public class PacemakerTest {

	private static final Hasher hasher = Sha256Hasher.withDefaultSerialization();

	private BFTNode self = mock(BFTNode.class);
	private SystemCounters counters = mock(SystemCounters.class);
	private HashSigner signer = mock(HashSigner.class);
	private VoteSender voteSender = mock(VoteSender.class);

	private PendingViewTimeouts pendingViewTimeouts = mock(PendingViewTimeouts.class);
	private BFTValidatorSet validatorSet = mock(BFTValidatorSet.class);
	private VertexStore vertexStore = mock(VertexStore.class);
	private ProposerElection proposerElection = mock(ProposerElection.class);
	private SafetyRules safetyRules = mock(SafetyRules.class);
	private PacemakerTimeoutSender timeoutSender = mock(PacemakerTimeoutSender.class);
	private EventDispatcher<View> timeoutDispatcher = rmock(EventDispatcher.class);

	private PacemakerState pacemakerState = mock(PacemakerState.class);
	private PacemakerTimeoutCalculator timeoutCalculator = mock(PacemakerTimeoutCalculator.class);
	private NextCommandGenerator nextCommandGenerator = mock(NextCommandGenerator.class);
	private ProposalBroadcaster proposalBroadcaster = mock(ProposalBroadcaster.class);

	private Pacemaker pacemaker;

	@Before
	public void setUp() {
		this.pacemaker = new Pacemaker(
			this.self,
			this.counters,
			this.pendingViewTimeouts,
			this.validatorSet,
			this.vertexStore,
			this.safetyRules,
			this.voteSender,
			this.timeoutDispatcher,
			this.pacemakerState,
			this.timeoutSender,
			this.timeoutCalculator,
			this.nextCommandGenerator,
			this.proposalBroadcaster,
			this.proposerElection,
			hasher
		);
	}

	@Test
	public void when_view_0_timeout__then_ignored() {
		ViewTimeout viewTimeout = mock(ViewTimeout.class);
		when(viewTimeout.getView()).thenReturn(View.of(0));
		this.pacemaker.processViewTimeout(viewTimeout);
		verifyNoMoreInteractions(this.timeoutSender);
	}

	@Test
	public void when_view_1_view_timeout_with_quorum__then_next_view_and_timeout_scheduled() {
		ViewTimeout viewTimeout = mock(ViewTimeout.class);
		HighQC hqc = mock(HighQC.class);
		QuorumCertificate highQC = mock(QuorumCertificate.class);
		BFTHeader header = mock(BFTHeader.class);
		LedgerHeader ledgerHeader = mock(LedgerHeader.class);

		when(viewTimeout.getView()).thenReturn(View.of(1));
		when(this.pendingViewTimeouts.insertViewTimeout(any(), any())).thenReturn(Optional.of(View.of(1)));
		when(hqc.highestQC()).thenReturn(highQC);
		when(highQC.getProposed()).thenReturn(header);
		when(header.getLedgerHeader()).thenReturn(ledgerHeader);
		when(ledgerHeader.isEndOfEpoch()).thenReturn(true);
		when(this.vertexStore.highQC()).thenReturn(hqc);
		when(this.signer.sign(Mockito.<HashCode>any())).thenReturn(new ECDSASignature());
		when(this.proposerElection.getProposer(eq(View.of(2)))).thenReturn(this.self);

		this.pacemaker.processViewTimeout(viewTimeout);

		verify(this.pacemakerState, times(1)).updateView(View.of(2));
	}

	@Test
	public void when_local_timeout_for_non_current_view__then_ignored() {
		this.pacemaker.processLocalTimeout(View.of(1));
		verifyNoMoreInteractions(this.proposerElection);
		verifyNoMoreInteractions(this.safetyRules);
		verifyNoMoreInteractions(this.pacemakerState);
	}
}
