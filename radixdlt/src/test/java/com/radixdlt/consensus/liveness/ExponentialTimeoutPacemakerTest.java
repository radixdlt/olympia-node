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
import com.radixdlt.crypto.Hasher;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import com.google.common.collect.Lists;
import com.radixdlt.consensus.BFTHeader;
import com.radixdlt.consensus.HashSigner;
import com.radixdlt.consensus.LedgerHeader;
import com.radixdlt.consensus.PendingVotes;
import com.radixdlt.consensus.Proposal;
import com.radixdlt.consensus.QuorumCertificate;
import com.radixdlt.consensus.UnverifiedVertex;
import com.radixdlt.consensus.ViewTimeout;
import com.radixdlt.consensus.Vote;
import com.radixdlt.consensus.HighQC;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.BFTValidatorSet;
import com.radixdlt.consensus.bft.VertexStore;
import com.radixdlt.consensus.liveness.ExponentialTimeoutPacemaker.PacemakerInfoSender;
import com.radixdlt.consensus.safety.SafetyRules;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.crypto.ECDSASignature;
import com.radixdlt.network.TimeSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

public class ExponentialTimeoutPacemakerTest {
	private static final double MAX_TIMEOUT = 30_000.0;

	static final class TimeoutHolder {
		final int pacemakerIndex;
		final View view;
		final long timeoutTime;

		TimeoutHolder(int pacemakerIndex, View view, long timeoutTime) {
			this.pacemakerIndex = pacemakerIndex;
			this.view = view;
			this.timeoutTime = timeoutTime;
		}
	}

	private long timeout = 500;
	private double rate = 1.2;
	// Want timeout increasing exponentially from timeout to MAX_TIMEOUT
	// = log_{rate} (MAXTIMEOUT / timeout)
	private int maxExponent = (int) Math.ceil(Math.log(MAX_TIMEOUT / this.timeout) / Math.log(this.rate));

	private BFTNode self = mock(BFTNode.class);
	private SystemCounters counters = mock(SystemCounters.class);
	private NextCommandGenerator nextCommandGenerator = mock(NextCommandGenerator.class);
	private TimeSupplier timeSupplier = mock(TimeSupplier.class);
	private Hasher hasher = mock(Hasher.class);
	private HashSigner signer = mock(HashSigner.class);
	private ProposalBroadcaster proposalBroadcaster = mock(ProposalBroadcaster.class);
	private ProceedToViewSender proceedToViewSender = mock(ProceedToViewSender.class);

	private PendingVotes pendingVotes = mock(PendingVotes.class);
	private PendingViewTimeouts pendingViewTimeouts = mock(PendingViewTimeouts.class);
	private BFTValidatorSet validatorSet = mock(BFTValidatorSet.class);
	private VertexStore vertexStore = mock(VertexStore.class);
	private ProposerElection proposerElection = mock(ProposerElection.class);
	private SafetyRules safetyRules = mock(SafetyRules.class);
	private PacemakerTimeoutSender timeoutSender = mock(PacemakerTimeoutSender.class);
	private PacemakerInfoSender infoSender = mock(PacemakerInfoSender.class);

	private ExponentialTimeoutPacemaker pacemaker;

	@Before
	public void setUp() {
		this.pacemaker = new ExponentialTimeoutPacemaker(
			this.timeout, this.rate, this.maxExponent,
			this.self,
			this.counters,
			this.pendingVotes,
			this.pendingViewTimeouts,
			this.validatorSet,
			this.vertexStore,
			this.proposerElection,
			this.safetyRules,
			this.nextCommandGenerator,
			this.timeSupplier,
			this.hasher,
			this.proposalBroadcaster,
			this.proceedToViewSender,
			this.timeoutSender,
			this.infoSender
		);
	}

	@Test
	public void when_creating_pacemaker_with_invalid_timeout__then_exception_is_thrown() {
		checkConstructionParams(0, 1.2, 1, "timeoutMilliseconds must be > 0");
		checkConstructionParams(-1, 1.2, 1, "timeoutMilliseconds must be > 0");
		checkConstructionParams(1, 1.0, 1, "rate must be > 1.0");
		checkConstructionParams(1, 1.2, -1, "maxExponent must be >= 0");
		checkConstructionParams(1, 100.0, 100, "Maximum timeout value");
	}

	@Test
	public void when_view_0_timeout__then_ignored() {
		ViewTimeout viewTimeout = mock(ViewTimeout.class);
		when(viewTimeout.getView()).thenReturn(View.of(0));

		this.pacemaker.processViewTimeout(viewTimeout);

		assertThat(this.pacemaker.getCurrentView()).isEqualTo(View.of(0));
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

		assertThat(this.pacemaker.getCurrentView()).isEqualTo(View.of(2));
		verify(this.timeoutSender, times(1)).scheduleTimeout(eq(View.of(2)), eq((long) (this.timeout * 1.2)));
		verify(this.proposalBroadcaster, times(1)).broadcastProposal(any(), any());
		verifyNoMoreInteractions(this.nextCommandGenerator);
	}

	@Test
	public void when_view_1_view_timeout_with_quorum__then_next_view_and_timeout_scheduled_with_command() {
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
		when(ledgerHeader.isEndOfEpoch()).thenReturn(false);
		when(this.vertexStore.highQC()).thenReturn(hqc);
		when(this.signer.sign(Mockito.<HashCode>any())).thenReturn(new ECDSASignature());
		when(this.proposerElection.getProposer(eq(View.of(2)))).thenReturn(this.self);

		this.pacemaker.processViewTimeout(viewTimeout);

		assertThat(this.pacemaker.getCurrentView()).isEqualTo(View.of(2));
		verify(this.timeoutSender, times(1)).scheduleTimeout(eq(View.of(2)), eq((long) (this.timeout * 1.2)));
		verify(this.proposalBroadcaster, times(1)).broadcastProposal(any(), any());
		verify(this.nextCommandGenerator, times(1)).generateNextCommand(any(), any());
	}

	@Test
	public void when_local_timeout_for_non_current_view__then_ignored() {
		this.pacemaker.processLocalTimeout(View.of(1));
		verifyNoMoreInteractions(this.proposerElection);
		verifyNoMoreInteractions(this.safetyRules);
	}

	@Test
	public void when_process_vote_equal_last_quorum__then_ignored() {
		Vote vote = mock(Vote.class);
		when(vote.getView()).thenReturn(View.of(0));

		assertThat(this.pacemaker.processVote(vote)).isEmpty();
		verifyNoMoreInteractions(this.pendingVotes);
	}

	@Test
	public void when_process_vote_with_quorum_wrong_view__then_ignored() {
		Vote vote = mock(Vote.class);
		QuorumCertificate qc = mock(QuorumCertificate.class);
		when(vote.getView()).thenReturn(View.of(1));
		when(this.pendingVotes.insertVote(any(), any())).thenReturn(Optional.of(qc));

		this.pacemaker.processQC(highQCFor(View.of(0)));
		this.pacemaker.processQC(highQCFor(View.of(1)));
		this.pacemaker.processQC(highQCFor(View.of(2)));
		assertThat(this.pacemaker.getCurrentView()).isEqualTo(View.of(3));

		assertThat(this.pacemaker.processVote(vote)).isEmpty();
		assertThat(this.pacemaker.getCurrentView()).isEqualTo(View.of(3));
		verifyNoMoreInteractions(this.pendingVotes);
	}

	@Test
	public void when_process_vote_with_quorum__then_processed() {
		Vote vote = mock(Vote.class);

		QuorumCertificate qc = mock(QuorumCertificate.class);
		when(vote.getView()).thenReturn(View.of(1));
		when(this.pendingVotes.insertVote(any(), any())).thenReturn(Optional.of(qc));

		// Move to view 1
		this.pacemaker.processQC(highQCFor(View.of(0)));
		assertThat(this.pacemaker.getCurrentView()).isEqualTo(View.of(1));

		assertThat(this.pacemaker.processVote(vote)).isNotEmpty();
		assertThat(this.pacemaker.getCurrentView()).isEqualTo(View.of(1));
		verify(this.pendingVotes, times(1)).insertVote(eq(vote), any());
		verifyNoMoreInteractions(this.pendingVotes);
	}

	@Test
	public void when_process_proposal_wrong_view__then_ignored() {
		Proposal proposal = mock(Proposal.class);
		when(proposal.getView()).thenReturn(View.of(1));

		this.pacemaker.processProposal(proposal);
		assertThat(this.pacemaker.getCurrentView()).isEqualTo(View.of(0));
		verifyNoMoreInteractions(this.vertexStore); // Nothing inserted
	}

	@Test
	public void when_process_proposal__then_vote_sent() {
		Proposal proposal = mock(Proposal.class);
		when(proposal.getView()).thenReturn(View.of(0));
		when(proposal.getVertex()).thenReturn(mock(UnverifiedVertex.class));
		when(this.hasher.hash(any())).thenReturn(mock(HashCode.class));
		when(this.vertexStore.insertVertex(any())).thenReturn(Optional.of(mock(BFTHeader.class)));
		when(this.safetyRules.voteFor(any(), any(), anyLong(), any())).thenReturn(Optional.of(mock(Vote.class)));

		this.pacemaker.processProposal(proposal);

		assertThat(this.pacemaker.getCurrentView()).isEqualTo(View.of(0));
		verify(this.vertexStore, times(1)).insertVertex(any());
		verify(this.proceedToViewSender, times(1)).sendVote(any(), any());
		verifyNoMoreInteractions(this.proceedToViewSender);
	}

	@Test
	public void when_process_proposal_safety_failure__then_vote_not_sent() {
		Proposal proposal = mock(Proposal.class);
		when(proposal.getView()).thenReturn(View.of(0));
		when(proposal.getVertex()).thenReturn(mock(UnverifiedVertex.class));
		when(this.hasher.hash(any())).thenReturn(mock(HashCode.class));
		when(this.safetyRules.voteFor(any(), any(), anyLong(), any())).thenReturn(Optional.empty());

		this.pacemaker.processProposal(proposal);
		assertThat(this.pacemaker.getCurrentView()).isEqualTo(View.of(0));
		verifyNoMoreInteractions(this.proceedToViewSender);
	}

	@Test
	public void when_process_qc_for_wrong_view__then_ignored() {
		HighQC highQC = mock(HighQC.class);
		QuorumCertificate qc = mock(QuorumCertificate.class);
		when(qc.getView()).thenReturn(View.of(1));
		when(highQC.highestQC()).thenReturn(qc);
		when(highQC.highestCommittedQC()).thenReturn(qc);

		// Move ahead for a bit so we can send in a QC for a lower view
		this.pacemaker.processQC(highQCFor(View.of(0)));
		this.pacemaker.processQC(highQCFor(View.of(1)));
		this.pacemaker.processQC(highQCFor(View.of(2)));
		assertThat(this.pacemaker.getCurrentView()).isEqualTo(View.of(3));

		assertThat(this.pacemaker.processQC(highQC)).isFalse();
		assertThat(this.pacemaker.getCurrentView()).isEqualTo(View.of(3));
	}

	@Test
	public void when_process_qc_for_current_view__then_processed() {
		HighQC highQC = mock(HighQC.class);
		QuorumCertificate qc = mock(QuorumCertificate.class);
		when(qc.getView()).thenReturn(View.of(0));
		when(highQC.highestQC()).thenReturn(qc);
		when(highQC.highestCommittedQC()).thenReturn(qc);

		assertThat(this.pacemaker.processQC(highQC)).isTrue();
		assertThat(this.pacemaker.getCurrentView()).isEqualTo(View.of(1));
		assertThat(this.pacemaker.highestCommitView()).isEqualTo(View.of(0));

		when(qc.getView()).thenReturn(View.of(1));
		assertThat(this.pacemaker.processQC(highQC)).isTrue();
		assertThat(this.pacemaker.getCurrentView()).isEqualTo(View.of(2));
		assertThat(this.pacemaker.highestCommitView()).isEqualTo(View.of(1));
	}

	@Test
	public void when_backoff_pow2_0__then_two_pacemakers_can_sync() {
		assertTrue(testSyncAtRate(2.0, Integer.MAX_VALUE, 1L << 32, View.of(33)));
	}

	@Test
	public void when_backoff_pow1_5__then_two_pacemakers_can_sync() {
		assertTrue(testSyncAtRate(1.5, Integer.MAX_VALUE, (long) Math.pow(1.5, 32.0), View.of(33)));
	}

	@Test
	public void when_backoff_pow1_2__then_two_pacemakers_can_sync() {
		assertTrue(testSyncAtRate(1.2, Integer.MAX_VALUE, (long) Math.pow(1.2, 32.0), View.of(39)));
	}

	@Test
	public void when_linear__then_two_pacemakers_do_not_sync() {
		assertFalse(testSyncAtRate(2.0, 0, 1L, View.of(58)));
	}

	@Test
	public void when_backoff_pow2_0__then_63_pacemakers_can_sync() {
		final double testRate = 2.0;
		final int numPacemakers = 63; // We want to stay in the exponential zone

		final AtomicLong baseTime = new AtomicLong(0L);
		final LinkedList<TimeoutHolder> timeouts = Lists.newLinkedList();

		final ExponentialTimeoutPacemaker[] pacemaker = new ExponentialTimeoutPacemaker[numPacemakers];

		when(this.pendingViewTimeouts.insertViewTimeout(any(), any())).thenAnswer(answer -> {
			ViewTimeout viewTimeout = answer.getArgument(0);
			return Optional.of(viewTimeout.getView());
		});
		when(this.proposerElection.getProposer(any())).thenReturn(mock(BFTNode.class)); // Not us, so no proposals

		for (int i = 0; i < numPacemakers; ++i) {
			final int j = i;
			final PacemakerTimeoutSender timeoutSender = (view, timeout) -> timeouts.push(new TimeoutHolder(j, view, baseTime.get() + timeout));
			pacemaker[i] = createPacemaker(1L, testRate, numPacemakers, timeoutSender);
		}

		// Preload with initial timeout for each pacemaker
		for (int i = 0; i < numPacemakers; ++i) {
			pacemaker[i].processQC(highQCFor(View.of(i)));
		}

		assertEquals(numPacemakers, timeouts.size());
		while (notSynced(pacemaker)) {
			assertFalse(anyViewGreaterThan(pacemaker, numPacemakers));
			assertEquals(numPacemakers, timeouts.size());
			timeouts.sort(Comparator.comparingLong(th -> th.timeoutTime));
			TimeoutHolder t0 = timeouts.pop();
			baseTime.set(t0.timeoutTime);
			pacemaker[t0.pacemakerIndex].processViewTimeout(viewTimeoutFor(t0.view));
		}

		for (int i = 0; i < numPacemakers; ++i) {
			assertEquals(numPacemakers, pacemaker[i].getCurrentView().number());
		}
	}

	private ViewTimeout viewTimeoutFor(View view) {
		ViewTimeout viewTimeout = mock(ViewTimeout.class);
		when(viewTimeout.getView()).thenReturn(view);
		return viewTimeout;
	}

	// Returns true if pacemakers synced, false otherwise
	private boolean testSyncAtRate(double testRate, int maxExponent, long setuptime, View catchupView) {
		final int maxMaxExponent = (int) Math.floor(Math.log(Long.MAX_VALUE) / Math.log(testRate));
		final int pacemakerMaxExponent = Math.min(maxExponent, maxMaxExponent);

		final AtomicLong baseTime = new AtomicLong(0L);
		final LinkedList<TimeoutHolder> timeouts = Lists.newLinkedList();

		final ExponentialTimeoutPacemaker[] pacemaker = new ExponentialTimeoutPacemaker[2];

		final PacemakerTimeoutSender timeoutSender0 = (view, timeout) -> timeouts.add(new TimeoutHolder(0, view, baseTime.get() + timeout));
		pacemaker[0] = createPacemaker(1L, testRate, pacemakerMaxExponent, timeoutSender0);

		PacemakerTimeoutSender timeoutSender1 = (view, timeout) -> timeouts.add(new TimeoutHolder(1, view, baseTime.get() + timeout));
		pacemaker[1] = createPacemaker(1L, testRate, pacemakerMaxExponent, timeoutSender1);

		when(this.pendingViewTimeouts.insertViewTimeout(any(), any())).thenAnswer(answer -> {
			ViewTimeout viewTimeout = answer.getArgument(0);
			return Optional.of(viewTimeout.getView());
		});
		when(this.proposerElection.getProposer(any())).thenReturn(mock(BFTNode.class)); // Not us, so no proposals

		// get pacemaker[0] at least 2 views ahead and until timeout > setuptime
		pacemaker[0].processQC(highQCFor(pacemaker[0].getCurrentView()));
		timeouts.pop();
		pacemaker[0].processQC(highQCFor(pacemaker[0].getCurrentView()));
		while (timeouts.getFirst().timeoutTime < setuptime) {
			pacemaker[0].processQC(highQCFor(timeouts.pop().view));
		}
		assertEquals(1, timeouts.size());

		pacemaker[1].processQC(highQCFor(pacemaker[1].getCurrentView())); // Timeout on 1 base time 0
		assertEquals(2, timeouts.size());
		while (notSynced(pacemaker)) {
			// If we move out of the exponential range, we are going to fail
			if (anyViewGreaterThan(pacemaker, maxMaxExponent)) {
				return false;
			}
			assertEquals(2, timeouts.size());
			timeouts.sort(Comparator.comparingLong(th -> th.timeoutTime));
			TimeoutHolder t0 = timeouts.pop();
			baseTime.set(t0.timeoutTime);
			pacemaker[t0.pacemakerIndex].processViewTimeout(viewTimeoutFor(t0.view.next()));
		}
		assertEquals(catchupView, pacemaker[0].getCurrentView());
		assertEquals(catchupView, pacemaker[1].getCurrentView());
		return true;
	}

	private boolean anyViewGreaterThan(ExponentialTimeoutPacemaker[] pacemaker, long maxView) {
		for (int i = 0; i < pacemaker.length; ++i) {
			if (pacemaker[i].getCurrentView().number() > maxView) {
				return true;
			}
		}
		return false;
	}

	private boolean notSynced(ExponentialTimeoutPacemaker[] pacemaker) {
		View v = pacemaker[0].getCurrentView();
		for (int i = 1; i < pacemaker.length; ++i) {
			if (!v.equals(pacemaker[i].getCurrentView())) {
				return true;
			}
		}
		return false;
	}

	private ExponentialTimeoutPacemaker createPacemaker(long timeout, double rate, int maxExponent, PacemakerTimeoutSender timeoutSender) {
		return new ExponentialTimeoutPacemaker(
			timeout, rate, maxExponent,
			this.self,
			this.counters,
			this.pendingVotes,
			this.pendingViewTimeouts,
			this.validatorSet,
			this.vertexStore,
			this.proposerElection,
			this.safetyRules,
			this.nextCommandGenerator,
			this.timeSupplier,
			this.hasher,
			this.proposalBroadcaster,
			this.proceedToViewSender,
			timeoutSender,
			this.infoSender
		);
	}

	private void checkConstructionParams(long timeout, double rate, int maxExponent, String exceptionMessage) {
		assertThatThrownBy(() -> createPacemaker(timeout, rate, maxExponent, this.timeoutSender))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageStartingWith(exceptionMessage);
	}

	private HighQC highQCFor(View view) {
		HighQC highQC = mock(HighQC.class);
		QuorumCertificate hqc = mock(QuorumCertificate.class);
		QuorumCertificate cqc = mock(QuorumCertificate.class);
		when(hqc.getView()).thenReturn(view);
		when(cqc.getView()).thenReturn(View.of(0));
		when(highQC.highestQC()).thenReturn(hqc);
		when(highQC.highestCommittedQC()).thenReturn(cqc);
		return highQC;
	}
}
