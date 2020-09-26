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

import com.google.common.collect.Lists;
import com.radixdlt.consensus.BFTHeader;
import com.radixdlt.consensus.NewView;
import com.radixdlt.consensus.QuorumCertificate;
import com.radixdlt.consensus.VerifiedLedgerHeaderAndProof;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.ValidationState;
import com.radixdlt.consensus.bft.BFTValidator;
import com.radixdlt.consensus.bft.BFTValidatorSet;
import com.radixdlt.crypto.ECDSASignature;
import org.junit.Before;

import com.radixdlt.utils.Pair;
import com.radixdlt.utils.UInt256;
import org.junit.Test;

import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

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

	private ExponentialTimeoutPacemaker pacemaker;
	private PacemakerTimeoutSender timeoutSender;
	private long timeout;
	private double rate;
	private int maxExponent;

	@Before
	public void setUp() {
		this.timeout = 500;
		this.rate = 1.2;
		// Want timeout increasing exponentially from timeout to MAX_TIMEOUT
		// = log_{rate} (MAXTIMEOUT / timeout)
		this.maxExponent = (int) Math.ceil(Math.log(MAX_TIMEOUT / this.timeout) / Math.log(this.rate));
		this.timeoutSender = mock(PacemakerTimeoutSender.class);
		this.pacemaker = new ExponentialTimeoutPacemaker(this.timeout, this.rate, this.maxExponent, this.timeoutSender);
	}

	@Test
	public void when_creating_pacemaker_with_invalid_timeout__then_exception_is_thrown() {
		assertThatThrownBy(() -> new ExponentialTimeoutPacemaker(0, 1.2, 1, this.timeoutSender))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageStartingWith("timeoutMilliseconds must be > 0");
		assertThatThrownBy(() -> new ExponentialTimeoutPacemaker(-1, 1.2, 1, this.timeoutSender))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageStartingWith("timeoutMilliseconds must be > 0");
		assertThatThrownBy(() -> new ExponentialTimeoutPacemaker(1, 1.0, 1, this.timeoutSender))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageStartingWith("rate must be > 1.0");
		assertThatThrownBy(() -> new ExponentialTimeoutPacemaker(1, 1.2, -1, this.timeoutSender))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageStartingWith("maxExponent must be >= 0");
		assertThatThrownBy(() -> new ExponentialTimeoutPacemaker(1, 100.0, 100, this.timeoutSender))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageStartingWith("Maximum timeout value");
	}

	@Test
	public void when_view_0_processed_qc__then_current_view_should_be_1_and_next_timeout_should_be_scheduled() {
		this.pacemaker.processNextView(View.of(0));
		assertThat(this.pacemaker.getCurrentView()).isEqualTo(View.of(1));
		verify(this.timeoutSender, times(1)).scheduleTimeout(eq(View.of(1)), eq(this.timeout));
	}

	@Test
	public void when_view_0_processed_timeout__then_current_view_should_be_1_and_next_timeout_should_be_scheduled() {
		this.pacemaker.processLocalTimeout(View.of(0));
		assertThat(this.pacemaker.getCurrentView()).isEqualTo(View.of(1));
		verify(this.timeoutSender, times(1)).scheduleTimeout(eq(View.of(1)), eq(this.timeout));
	}

	@Test
	public void when_process_timeout_twice__then_two_timeout_events_occur() {
		double exponential = Math.pow(this.rate, Math.min(this.maxExponent, 1));
		long secondTimeout = Math.round(this.timeout * exponential);

		this.pacemaker.processLocalTimeout(View.of(0));
		this.pacemaker.processLocalTimeout(View.of(1));
		verify(this.timeoutSender, times(1)).scheduleTimeout(any(), eq(this.timeout));
		verify(this.timeoutSender, times(1)).scheduleTimeout(any(), eq(secondTimeout));
	}

	@Test
	public void when_process_timeout_for_earlier_view__then_view_should_not_change() {
		assertThat(pacemaker.getCurrentView()).isEqualTo(View.of(0L));
		Optional<View> newView = pacemaker.processNextView(View.of(0L));
		assertThat(newView).isEqualTo(Optional.of(View.of(1L)));
		assertThat(pacemaker.getCurrentView()).isEqualByComparingTo(View.of(1L));
		pacemaker.processLocalTimeout(View.of(0L));
		assertThat(pacemaker.getCurrentView()).isEqualByComparingTo(View.of(1L));
	}

	@Test
	public void when_process_qc_twice_for_same_view__then_view_should_not_change() {
		assertThat(pacemaker.getCurrentView()).isEqualByComparingTo(View.of(0L));
		Optional<View> newView = pacemaker.processNextView(View.of(0L));
		assertThat(newView).isEqualTo(Optional.of(View.of(1L)));
		assertThat(pacemaker.getCurrentView()).isEqualByComparingTo(View.of(1L));
		newView = pacemaker.processNextView(View.of(0L));
		assertThat(newView).isEmpty();
		assertThat(pacemaker.getCurrentView()).isEqualByComparingTo(View.of(1L));
		assertThat(pacemaker.getCurrentView()).isEqualByComparingTo(View.of(1L));
	}

	@Test
	public void when_inserting_a_new_view_without_signature__then_exception_is_thrown() {
		NewView newViewWithoutSignature = mock(NewView.class);
		QuorumCertificate qc = mock(QuorumCertificate.class);
		when(qc.getView()).thenReturn(View.of(1L));
		when(newViewWithoutSignature.getQC()).thenReturn(qc);
		when(newViewWithoutSignature.getView()).thenReturn(View.of(2L));
		when(newViewWithoutSignature.getSignature()).thenReturn(Optional.empty());
		when(newViewWithoutSignature.getAuthor()).thenReturn(mock(BFTNode.class));
		BFTValidatorSet validatorSet = mock(BFTValidatorSet.class);
		when(validatorSet.containsNode(any())).thenReturn(true);

		assertThatThrownBy(() -> pacemaker.processNewView(newViewWithoutSignature, validatorSet))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	public void when_inserting_valid_but_unaccepted_new_views__then_no_new_view_is_returned() {
		View view = View.of(2);
		NewView newView1 = makeNewViewFor(view);
		NewView newView2 = makeNewViewFor(view);
		BFTValidatorSet validatorSet = BFTValidatorSet.from(
			Collections.singleton(BFTValidator.from(newView1.getAuthor(), UInt256.ONE))
		);
		assertThat(pacemaker.processNewView(newView2, validatorSet)).isEmpty();
	}

	@Test
	public void when_inserting_valid_but_old_new_views__then_no_new_view_is_returned() {
		View view = View.of(0);
		NewView newView = makeNewViewFor(view);
		BFTValidatorSet validatorSet = mock(BFTValidatorSet.class);
		pacemaker.processNextView(View.of(0));
		assertThat(pacemaker.processNewView(newView, validatorSet)).isEmpty();
	}

	@Test
	public void when_inserting_current_and_accepted_new_views__then_qc_is_formed_and_current_view_has_changed() {
		View view = View.of(1);
		NewView newView = makeNewViewFor(view);
		BFTValidatorSet validatorSet = mock(BFTValidatorSet.class);
		ValidationState validationState = mock(ValidationState.class);
		when(validationState.addSignature(any(), anyLong(), any())).thenReturn(true);
		when(validationState.complete()).thenReturn(true);
		when(validatorSet.newValidationState()).thenReturn(validationState);
		when(validatorSet.containsNode(any())).thenReturn(true);
		pacemaker.processNextView(View.of(0));

		assertThat(pacemaker.processNewView(newView, validatorSet)).isPresent().get().isEqualTo(View.of(1));
		assertThat(pacemaker.getCurrentView()).isEqualTo(View.of(1));
	}

	@Test
	public void when_inserting_new_view_with_qc_from_previous_view__then_new_synced_view_is_returned() {
		View view = View.of(2);
		BFTNode node = mock(BFTNode.class);

		NewView newView = mock(NewView.class);
		when(newView.getView()).thenReturn(view);
		when(newView.getSignature()).thenReturn(Optional.of(new ECDSASignature()));
		when(newView.getAuthor()).thenReturn(node);

		QuorumCertificate qc = mock(QuorumCertificate.class);
		when(qc.getView()).thenReturn(View.of(1));
		when(newView.getQC()).thenReturn(qc);

		BFTValidatorSet validatorSet = mock(BFTValidatorSet.class);
		pacemaker.processNextView(View.of(1));

		assertThat(pacemaker.processNewView(newView, validatorSet))
			.isPresent().get().isEqualTo(View.of(2));
		assertThat(pacemaker.getCurrentView())
			.isEqualTo(View.of(2));
	}

	@Test
	public void when_quorum_formed_for_wrong_view__then_current_view_not_changed_and_no_new_timeout() {
		View view = View.of(2);
		NewView newView = makeNewViewFor(view);
		QuorumCertificate qc = mock(QuorumCertificate.class);
		when(qc.getView()).thenReturn(View.genesis());
		when(newView.getQC()).thenReturn(qc);
		BFTValidatorSet validatorSet = mock(BFTValidatorSet.class);
		ValidationState validationState = mock(ValidationState.class);
		when(validationState.addSignature(any(), anyLong(), any())).thenReturn(true);
		when(validationState.complete()).thenReturn(true);
		when(validatorSet.newValidationState()).thenReturn(validationState);
		when(validatorSet.containsNode(any())).thenReturn(true);

		// Move to view 1
		this.pacemaker.processNextView(View.genesis());
		verify(this.timeoutSender, times(1)).scheduleTimeout(eq(View.of(1)), eq(this.timeout));

		// Process new view message
		assertThat(this.pacemaker.processNewView(newView, validatorSet)).isEmpty();
		assertThat(this.pacemaker.getCurrentView()).isEqualTo(View.of(1));
		verifyNoMoreInteractions(this.timeoutSender);
	}

	@Test
	public void when_processing_qc_with_commit__highest_commit_view_updated() {
		View view = View.of(2);
		QuorumCertificate qc = mock(QuorumCertificate.class);
		when(qc.getView()).thenReturn(view);
		when(qc.getCommittedAndLedgerStateProof())
			.thenReturn(Optional.of(Pair.of(mock(BFTHeader.class), mock(VerifiedLedgerHeaderAndProof.class))));

		this.pacemaker.processQC(qc);

		verify(this.timeoutSender, times(1)).scheduleTimeout(eq(view.next()), eq(this.timeout));
		assertThat(this.pacemaker.getCurrentView()).isEqualTo(view.next());
		assertThat(this.pacemaker.highestCommitView()).isEqualTo(view);
	}

	@Test
	public void when_processing_qc_with_old_commit__highest_commit_view_not_updated() {
		View view2 = View.of(2);
		QuorumCertificate qc2 = mock(QuorumCertificate.class);
		when(qc2.getView()).thenReturn(view2);
		when(qc2.getCommittedAndLedgerStateProof())
			.thenReturn(Optional.of(Pair.of(mock(BFTHeader.class), mock(VerifiedLedgerHeaderAndProof.class))));

		this.pacemaker.processQC(qc2);

		assertThat(this.pacemaker.getCurrentView()).isEqualTo(view2.next());
		assertThat(this.pacemaker.highestCommitView()).isEqualTo(view2);

		View view1 = View.of(1);
		QuorumCertificate qc1 = mock(QuorumCertificate.class);
		when(qc1.getView()).thenReturn(view1);
		when(qc1.getCommittedAndLedgerStateProof())
			.thenReturn(Optional.of(Pair.of(mock(BFTHeader.class), mock(VerifiedLedgerHeaderAndProof.class))));

		this.pacemaker.processQC(qc1);
		assertThat(this.pacemaker.getCurrentView()).isEqualTo(view2.next());
		assertThat(this.pacemaker.highestCommitView()).isEqualTo(view2);
	}

	@Test
	public void when_processing_qc_without_commit__highest_commit_view_not_updated() {
		View view = View.of(2);
		QuorumCertificate qc = mock(QuorumCertificate.class);
		when(qc.getView()).thenReturn(view);
		when(qc.getCommittedAndLedgerStateProof()).thenReturn(Optional.empty());

		this.pacemaker.processQC(qc);

		long expectedTimeout = Math.round(this.timeout * Math.pow(this.rate, 2));
		verify(this.timeoutSender, times(1)).scheduleTimeout(eq(view.next()), eq(expectedTimeout));
		assertThat(this.pacemaker.getCurrentView()).isEqualTo(view.next());
		assertThat(this.pacemaker.highestCommitView()).isEqualTo(View.genesis());
	}

	@Test
	public void when_backoff_pow2_0__then_two_pacemakers_can_sync() {
		assertTrue(testSyncAtRate(2.0, Integer.MAX_VALUE, 1L << 32, View.of(33)));
	}

	@Test
	public void when_backoff_pow1_5__then_two_pacemakers_can_sync() {
		assertTrue(testSyncAtRate(1.5, Integer.MAX_VALUE, (long) Math.pow(1.5, 32.0), View.of(35)));
	}

	@Test
	public void when_backoff_pow1_2__then_two_pacemakers_can_sync() {
		assertTrue(testSyncAtRate(1.2, Integer.MAX_VALUE, (long) Math.pow(1.2, 32.0), View.of(42)));
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

		for (int i = 0; i < numPacemakers; ++i) {
			final int j = i;
			final PacemakerTimeoutSender timeoutSender = (view, timeout) -> timeouts.push(new TimeoutHolder(j, view, baseTime.get() + timeout));
			pacemaker[i] = new ExponentialTimeoutPacemaker(1L, testRate, numPacemakers, timeoutSender);
		}

		for (int i = 1; i < numPacemakers; ++i) {
			for (int j = i; j < numPacemakers; ++j) {
				pacemaker[j].processLocalTimeout(pacemaker[j].getCurrentView());
				timeouts.pop();
			}
		}

		assertTrue(timeouts.isEmpty());
		// Preload with initial timeout for each pacemaker
		for (int i = 0; i < numPacemakers; ++i) {
			pacemaker[i].processLocalTimeout(pacemaker[i].getCurrentView());
		}

		while (notSynced(pacemaker)) {
			assertFalse(anyViewGreaterThan(pacemaker, numPacemakers));
			assertEquals(numPacemakers, timeouts.size());
			timeouts.sort(Comparator.comparingLong(th -> th.timeoutTime));
			TimeoutHolder t0 = timeouts.pop();
			baseTime.set(t0.timeoutTime);
			pacemaker[t0.pacemakerIndex].processNextView(t0.view);
		}

		for (int i = 0; i < numPacemakers; ++i) {
			assertEquals(numPacemakers, pacemaker[i].getCurrentView().number());
		}
	}

	// Returns true if pacemakers synced, false otherwise
	public boolean testSyncAtRate(double testRate, int maxExponent, long setuptime, View catchupView) {
		final int maxMaxExponent = (int) Math.floor(Math.log(Long.MAX_VALUE) / Math.log(testRate));
		final int pacemakerMaxExponent = Math.min(maxExponent, maxMaxExponent);

		final AtomicLong baseTime = new AtomicLong(0L);
		final LinkedList<TimeoutHolder> timeouts = Lists.newLinkedList();

		final ExponentialTimeoutPacemaker[] pacemaker = new ExponentialTimeoutPacemaker[2];

		final PacemakerTimeoutSender timeoutSender0 = (view, timeout) -> timeouts.add(new TimeoutHolder(0, view, baseTime.get() + timeout));
		pacemaker[0] = new ExponentialTimeoutPacemaker(1L, testRate, pacemakerMaxExponent, timeoutSender0);

		PacemakerTimeoutSender timeoutSender1 = (view, timeout) -> timeouts.add(new TimeoutHolder(1, view, baseTime.get() + timeout));
		pacemaker[1] = new ExponentialTimeoutPacemaker(1L, testRate, pacemakerMaxExponent, timeoutSender1);

		// get pacemaker[0] at least 2 views ahead and until timeout > setuptime
		pacemaker[0].processNextView(pacemaker[0].getCurrentView());
		timeouts.pop();
		pacemaker[0].processNextView(pacemaker[0].getCurrentView());
		while (timeouts.getFirst().timeoutTime < setuptime) {
			pacemaker[0].processNextView(timeouts.pop().view);
		}
		assertEquals(1, timeouts.size());

		pacemaker[1].processNextView(pacemaker[1].getCurrentView()); // Timeout on 1 base time 0
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
			pacemaker[t0.pacemakerIndex].processNextView(t0.view);
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

	private NewView makeNewViewFor(View view) {
		NewView newView = mock(NewView.class);
		QuorumCertificate qc = mock(QuorumCertificate.class);
		if (!view.isGenesis()) {
			when(qc.getView()).thenReturn(view.previous());
		}
		when(newView.getQC()).thenReturn(qc);
		when(newView.getView()).thenReturn(view);
		when(newView.getSignature()).thenReturn(Optional.of(new ECDSASignature()));
		BFTNode node = mock(BFTNode.class);
		when(newView.getAuthor()).thenReturn(node);
		return newView;
	}
}
