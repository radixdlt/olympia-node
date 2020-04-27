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

import com.radixdlt.consensus.NewView;
import com.radixdlt.consensus.QuorumCertificate;
import com.radixdlt.consensus.View;
import com.radixdlt.consensus.validators.ValidationState;
import com.radixdlt.consensus.validators.Validator;
import com.radixdlt.consensus.validators.ValidatorSet;
import com.radixdlt.crypto.ECDSASignature;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.utils.UInt256;
import io.reactivex.rxjava3.observers.TestObserver;
import org.junit.Test;

import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class PacemakerImplTest {
	private static final int TEST_PACEMAKER_TIMEOUT = 100;

	private static ScheduledExecutorService getMockedExecutorService() {
		ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();

		ScheduledExecutorService executorService = mock(ScheduledExecutorService.class);
		doAnswer(invocation -> {
			// schedule submissions with a small timeout to ensure that control is returned before the
			// "scheduled" runnable is executed, otherwise required events may not be triggered in time
			executor.schedule((Runnable) invocation.getArguments()[0], 10, TimeUnit.MILLISECONDS);
			return null;
		}).when(executorService).schedule(any(Runnable.class), anyLong(), any());
		return executorService;
	}

	@Test
	public void when_creating_pacemaker_with_invalid_timeout__then_exception_is_thrown() {
		ScheduledExecutorService executorService = getMockedExecutorService();
		assertThatThrownBy(() -> new PacemakerImpl(0, executorService));
		assertThatThrownBy(() -> new PacemakerImpl(-1, executorService));
		assertThatThrownBy(() -> new PacemakerImpl(-100, executorService));
	}

	@Test
	public void when_start__then_a_timeout_event_with_view_0_is_emitted() {
		ScheduledExecutorService executorService = getMockedExecutorService();
		PacemakerImpl pacemaker = new PacemakerImpl(TEST_PACEMAKER_TIMEOUT, executorService);
		TestObserver<View> testObserver = TestObserver.create();
		pacemaker.localTimeouts().subscribe(testObserver);
		testObserver.awaitCount(1);
		testObserver.assertNotComplete();
		testObserver.assertValues(View.of(0L));
	}

	@Test
	public void when_view_0_processed_qc__then_current_view_should_be_1_and_next_timeout_should_be_scheduled() {
		ScheduledExecutorService executorService = getMockedExecutorService();
		PacemakerImpl pacemaker = new PacemakerImpl(TEST_PACEMAKER_TIMEOUT, executorService);
		TestObserver<View> testObserver = TestObserver.create();
		pacemaker.localTimeouts().subscribe(testObserver);
		pacemaker.processQC(View.of(0L));
		assertThat(pacemaker.getCurrentView()).isEqualTo(View.of(1L));
		verify(executorService, times(2)).schedule(any(Runnable.class), anyLong(), any());
	}

	@Test
	public void when_view_0_processed_timeout__then_current_view_should_be_1_and_next_timeout_should_be_scheduled() {
		ScheduledExecutorService executorService = getMockedExecutorService();
		PacemakerImpl pacemaker = new PacemakerImpl(TEST_PACEMAKER_TIMEOUT, executorService);
		TestObserver<View> testObserver = TestObserver.create();
		pacemaker.localTimeouts().subscribe(testObserver);
		pacemaker.processLocalTimeout(View.of(0L));
		assertThat(pacemaker.getCurrentView()).isEqualTo(View.of(1L));
		verify(executorService, times(2)).schedule(any(Runnable.class), anyLong(), any());
	}

	@Test
	public void when_timeout_event_occurs_and_no_process__then_no_scheduled_timeout_occurs() {
		ScheduledExecutorService executorService = getMockedExecutorService();
		PacemakerImpl pacemaker = new PacemakerImpl(TEST_PACEMAKER_TIMEOUT, executorService);
		TestObserver<View> testObserver = TestObserver.create();
		pacemaker.localTimeouts().subscribe(testObserver);
		testObserver.awaitCount(1);
		testObserver.assertValueCount(1);
		testObserver.assertNotComplete();
		verify(executorService, times(1)).schedule(any(Runnable.class), anyLong(), any());
	}

	@Test
	public void when_process_timeout__then_two_timeout_events_occur() {
		ScheduledExecutorService executorService = getMockedExecutorService();
		PacemakerImpl pacemaker = new PacemakerImpl(TEST_PACEMAKER_TIMEOUT, executorService);
		TestObserver<View> testObserver = TestObserver.create();
		pacemaker.localTimeouts().subscribe(testObserver);
		testObserver.awaitCount(1);
		testObserver.assertValue(View.of(0L));
		pacemaker.processLocalTimeout(View.of(0L));
		testObserver.awaitCount(2);
		testObserver.assertValues(View.of(0L), View.of(1L));
	}

	@Test
	public void when_process_timeout_for_earlier_view__then_view_should_not_change() {
		ScheduledExecutorService executorService = getMockedExecutorService();
		PacemakerImpl pacemaker = new PacemakerImpl(TEST_PACEMAKER_TIMEOUT, executorService);
		assertThat(pacemaker.getCurrentView()).isEqualByComparingTo(View.of(0L));
		Optional<View> newView = pacemaker.processQC(View.of(0L));
		assertThat(newView).isEqualTo(Optional.of(View.of(1L)));
		assertThat(pacemaker.getCurrentView()).isEqualByComparingTo(View.of(1L));
		pacemaker.processLocalTimeout(View.of(0L));
		assertThat(pacemaker.getCurrentView()).isEqualByComparingTo(View.of(1L));
	}

	@Test
	public void when_process_qc_twice_for_same_view__then_view_should_not_change() {
		ScheduledExecutorService executorService = getMockedExecutorService();
		PacemakerImpl pacemaker = new PacemakerImpl(TEST_PACEMAKER_TIMEOUT, executorService);
		TestObserver<View> testObserver = TestObserver.create();
		pacemaker.localTimeouts().subscribe(testObserver);
		assertThat(pacemaker.getCurrentView()).isEqualByComparingTo(View.of(0L));
		Optional<View> newView = pacemaker.processQC(View.of(0L));
		assertThat(newView).isEqualTo(Optional.of(View.of(1L)));
		assertThat(pacemaker.getCurrentView()).isEqualByComparingTo(View.of(1L));
		newView = pacemaker.processQC(View.of(0L));
		assertThat(newView).isEmpty();
		assertThat(pacemaker.getCurrentView()).isEqualByComparingTo(View.of(1L));
		assertThat(pacemaker.getCurrentView()).isEqualByComparingTo(View.of(1L));
	}

	@Test
	public void when_inserting_a_new_view_without_signature__then_exception_is_thrown() {
		ScheduledExecutorService executorService = getMockedExecutorService();
		PacemakerImpl pacemaker = new PacemakerImpl(TEST_PACEMAKER_TIMEOUT, executorService);
		NewView newViewWithoutSignature = mock(NewView.class);
		when(newViewWithoutSignature.getView()).thenReturn(View.of(2L));
		when(newViewWithoutSignature.getSignature()).thenReturn(Optional.empty());

		assertThatThrownBy(() -> pacemaker.processNewView(newViewWithoutSignature, mock(ValidatorSet.class)));
	}

	@Test
	public void when_inserting_valid_but_unaccepted_new_views__then_no_new_view_is_returned() {
		View view = View.of(2);
		NewView newView1 = makeNewViewFor(view);
		NewView newView2 = makeNewViewFor(view);
		ValidatorSet validatorSet = ValidatorSet.from(Collections.singleton(Validator.from(newView1.getAuthor(), UInt256.ONE)));
		ScheduledExecutorService executorService = getMockedExecutorService();
		PacemakerImpl pacemaker = new PacemakerImpl(TEST_PACEMAKER_TIMEOUT, executorService);
		assertThat(pacemaker.processNewView(newView2, validatorSet)).isEmpty();
	}

	@Test
	public void when_inserting_valid_but_old_new_views__then_no_new_view_is_returned() {
		View view = View.of(0);
		NewView newView = makeNewViewFor(view);
		ValidatorSet validatorSet = mock(ValidatorSet.class);
		ScheduledExecutorService executorService = getMockedExecutorService();
		PacemakerImpl pacemaker = new PacemakerImpl(TEST_PACEMAKER_TIMEOUT, executorService);
		pacemaker.processQC(View.of(0));
		assertThat(pacemaker.processNewView(newView, validatorSet)).isEmpty();
	}

	@Test
	public void when_inserting_current_and_accepted_new_views__then_qc_is_formed_and_current_view_has_changed() {
		View view = View.of(1);
		NewView newView = makeNewViewFor(view);
		ValidatorSet validatorSet = mock(ValidatorSet.class);
		ValidationState validationState = mock(ValidationState.class);
		when(validationState.addSignature(any(), any())).thenReturn(true);
		when(validatorSet.newValidationState(any())).thenReturn(validationState);
		ScheduledExecutorService executorService = getMockedExecutorService();
		PacemakerImpl pacemaker = new PacemakerImpl(TEST_PACEMAKER_TIMEOUT, executorService);
		pacemaker.processQC(View.of(0));

		assertThat(pacemaker.processNewView(newView, validatorSet)).isPresent().get().isEqualTo(View.of(1));
		assertThat(pacemaker.getCurrentView()).isEqualTo(View.of(1));
	}

	@Test
	public void when_inserting_new_view_with_qc_from_previous_view__then_new_synced_view_is_returned() {
		View view = View.of(2);
		ECPublicKey author = ECKeyPair.generateNew().getPublicKey();

		NewView newView = mock(NewView.class);
		when(newView.getView()).thenReturn(view);
		when(newView.getSignature()).thenReturn(Optional.of(new ECDSASignature()));
		when(newView.getAuthor()).thenReturn(author);

		QuorumCertificate qc = mock(QuorumCertificate.class);
		when(qc.getView()).thenReturn(View.of(1));
		when(newView.getQC()).thenReturn(qc);

		ValidatorSet validatorSet = mock(ValidatorSet.class);
		ScheduledExecutorService executorService = getMockedExecutorService();

		PacemakerImpl pacemaker = new PacemakerImpl(TEST_PACEMAKER_TIMEOUT, executorService);
		pacemaker.processQC(View.of(1));

		assertThat(pacemaker.processNewView(newView, validatorSet))
			.isPresent().get().isEqualTo(View.of(2));
		assertThat(pacemaker.getCurrentView())
			.isEqualTo(View.of(2));
	}

	@Test
	public void when_inserting_new_views_with_non_current_view_qc__then_current_view_has_not_changed_and_no_new_timeout() {
		View view = View.of(2);
		NewView newView = makeNewViewFor(view);
		QuorumCertificate qc = mock(QuorumCertificate.class);
		when(qc.getView()).thenReturn(View.genesis());
		when(newView.getQC()).thenReturn(qc);
		ValidatorSet validatorSet = mock(ValidatorSet.class);
		ValidationState validationState = mock(ValidationState.class);
		when(validationState.complete()).thenReturn(true);
		when(validatorSet.newValidationState(any())).thenReturn(validationState);

		ScheduledExecutorService executorService = getMockedExecutorService();

		PacemakerImpl pacemaker = new PacemakerImpl(TEST_PACEMAKER_TIMEOUT, executorService);
		pacemaker.processQC(View.genesis());

		assertThat(pacemaker.processNewView(newView, validatorSet)).isEmpty();
		assertThat(pacemaker.getCurrentView()).isEqualTo(View.of(1));
		verify(executorService, times(1)).schedule(any(Runnable.class), anyLong(), any());
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
		when(newView.getAuthor()).thenReturn(ECKeyPair.generateNew().getPublicKey());
		return newView;
	}
}