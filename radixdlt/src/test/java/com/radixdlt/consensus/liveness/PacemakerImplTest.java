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

import com.google.common.collect.ImmutableList;
import com.radixdlt.consensus.NewView;
import com.radixdlt.consensus.QuorumCertificate;
import com.radixdlt.consensus.View;
import com.radixdlt.consensus.validators.ValidationResult;
import com.radixdlt.consensus.validators.Validator;
import com.radixdlt.consensus.validators.ValidatorSet;
import com.radixdlt.crypto.CryptoException;
import com.radixdlt.crypto.ECDSASignature;
import com.radixdlt.crypto.ECKeyPair;
import io.reactivex.rxjava3.observers.TestObserver;
import java.util.Collections;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.Test;

import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;

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
	private static ScheduledExecutorService getMockedExecutorService() {
		ExecutorService executor = Executors.newSingleThreadExecutor();

		ScheduledExecutorService executorService = mock(ScheduledExecutorService.class);
		doAnswer(invocation -> {
			executor.submit((Runnable) invocation.getArguments()[0]);
			return null;
		}).when(executorService).schedule(any(Runnable.class), anyLong(), any());
		return executorService;
	}

	@Test
	public void when_start__then_a_timeout_event_with_view_0_is_emitted() {
		ScheduledExecutorService executorService = getMockedExecutorService();
		PacemakerImpl pacemaker = new PacemakerImpl(executorService);
		TestObserver<View> testObserver = TestObserver.create();
		pacemaker.localTimeouts().subscribe(testObserver);
		testObserver.awaitCount(1);
		testObserver.assertValues(View.of(0L));
		testObserver.assertNotComplete();
	}

	@Test
	public void when_view_0_processed_qc__then_current_view_should_be_1_and_next_timeout_should_be_scheduled() {
		ScheduledExecutorService executorService = getMockedExecutorService();
		PacemakerImpl pacemaker = new PacemakerImpl(executorService);
		TestObserver<View> testObserver = TestObserver.create();
		pacemaker.localTimeouts().subscribe(testObserver);
		pacemaker.processQC(View.of(0L));
		assertThat(pacemaker.getCurrentView()).isEqualTo(View.of(1L));
		verify(executorService, times(2)).schedule(any(Runnable.class), anyLong(), any());
	}

	@Test
	public void when_view_0_processed_timeout__then_current_view_should_be_1_and_next_timeout_should_be_scheduled() {
		ScheduledExecutorService executorService = getMockedExecutorService();
		PacemakerImpl pacemaker = new PacemakerImpl(executorService);
		TestObserver<View> testObserver = TestObserver.create();
		pacemaker.localTimeouts().subscribe(testObserver);
		pacemaker.processLocalTimeout(View.of(0L));
		assertThat(pacemaker.getCurrentView()).isEqualTo(View.of(1L));
		verify(executorService, times(2)).schedule(any(Runnable.class), anyLong(), any());
	}

	@Test
	public void when_timeout_event_occurs_and_no_process__then_no_scheduled_timeout_occurs() {
		ScheduledExecutorService executorService = getMockedExecutorService();
		PacemakerImpl pacemaker = new PacemakerImpl(executorService);
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
		PacemakerImpl pacemaker = new PacemakerImpl(executorService);
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
		PacemakerImpl pacemaker = new PacemakerImpl(executorService);
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
		PacemakerImpl pacemaker = new PacemakerImpl(executorService);
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
		PacemakerImpl pacemaker = new PacemakerImpl(executorService);
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
		ValidatorSet validatorSet = ValidatorSet.from(Collections.singleton(Validator.from(newView1.getAuthor())));
		ScheduledExecutorService executorService = getMockedExecutorService();
		PacemakerImpl pacemaker = new PacemakerImpl(executorService);
		assertThat(pacemaker.processNewView(newView2, validatorSet)).isEmpty();
	}

	@Test
	public void when_inserting_current_and_accepted_new_views__then_qc_is_formed_and_current_view_has_changed_and_no_new_timeout() {
		View view = View.of(0);
		NewView newView = makeNewViewFor(view);
		ValidatorSet validatorSet = mock(ValidatorSet.class);
		ValidationResult result = mock(ValidationResult.class);
		when(result.valid()).thenReturn(true);
		when(validatorSet.validate(any(), any())).thenReturn(result);
		ScheduledExecutorService executorService = getMockedExecutorService();
		PacemakerImpl pacemaker = new PacemakerImpl(executorService);
		assertThat(pacemaker.processNewView(newView, validatorSet)).isPresent().get().isEqualTo(View.of(0));
		assertThat(pacemaker.getCurrentView()).isEqualTo(View.of(0));
		verify(executorService, times(0)).schedule(any(Runnable.class), anyLong(), any());
	}

	@Test
	public void when_inserting_new_and_accepted_new_views__then_qc_is_formed_and_current_view_has_changed_and_new_timeout() {
		View view = View.of(2);
		NewView newView = makeNewViewFor(view);
		ValidatorSet validatorSet = mock(ValidatorSet.class);
		ValidationResult result = mock(ValidationResult.class);
		when(result.valid()).thenReturn(true);
		when(validatorSet.validate(any(), any())).thenReturn(result);
		ScheduledExecutorService executorService = getMockedExecutorService();
		PacemakerImpl pacemaker = new PacemakerImpl(executorService);
		assertThat(pacemaker.processNewView(newView, validatorSet)).isPresent().get().isEqualTo(View.of(2));
		assertThat(pacemaker.getCurrentView()).isEqualTo(View.of(2));
		verify(executorService, times(1)).schedule(any(Runnable.class), anyLong(), any());
	}

	private NewView makeNewViewFor(View view) {
		NewView newView = mock(NewView.class);
		when(newView.getView()).thenReturn(view);
		when(newView.getSignature()).thenReturn(Optional.of(new ECDSASignature()));
		try {
			when(newView.getAuthor()).thenReturn(new ECKeyPair().getPublicKey());
		} catch (CryptoException e) {
			throw new RuntimeException("Failed to setup new-view", e);
		}
		return newView;
	}

	@Test
	public void when_process_new_view_and_is_a_quorum__should_return_new_view() throws Exception {
		ScheduledExecutorService executorService = getMockedExecutorService();
		ValidatorSet validatorSet = mock(ValidatorSet.class);
		when(validatorSet.validate(any(), any())).thenReturn(ValidationResult.passed(ImmutableList.of(mock(Validator.class))));
		PacemakerImpl pacemaker = new PacemakerImpl(executorService);

		View view = mock(View.class);
		ECKeyPair keyPair = new ECKeyPair();
		NewView newView = new NewView(keyPair.getPublicKey(), view, mock(QuorumCertificate.class), mock(ECDSASignature.class));
		assertThat(pacemaker.processNewView(newView, validatorSet))
			.get()
			.isEqualTo(view);
	}
}