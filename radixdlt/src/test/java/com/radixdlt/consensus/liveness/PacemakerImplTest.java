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

import com.radixdlt.consensus.View;
import io.reactivex.rxjava3.observers.TestObserver;
import org.junit.Test;

import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class PacemakerImplTest {
	private static ScheduledExecutorService getMockedExecutorService() {
		ScheduledExecutorService executorService = mock(ScheduledExecutorService.class);
		doAnswer((invocation) -> {
			((Runnable) invocation.getArguments()[0]).run();
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
		pacemaker.start();
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
		pacemaker.start();
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
		pacemaker.start();
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
		pacemaker.start();
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
		pacemaker.start();
		pacemaker.processLocalTimeout(View.of(0L));
		testObserver.awaitCount(2);
		testObserver.assertValues(View.of(0L), View.of(1L));
	}

	@Test
	public void when_process_timeout_for_earlier_view__then_view_should_not_change() {
		ScheduledExecutorService executorService = getMockedExecutorService();
		PacemakerImpl pacemaker = new PacemakerImpl(executorService);
		pacemaker.start();
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
		pacemaker.start();
		assertThat(pacemaker.getCurrentView()).isEqualByComparingTo(View.of(0L));
		Optional<View> newView = pacemaker.processQC(View.of(0L));
		assertThat(newView).isEqualTo(Optional.of(View.of(1L)));
		assertThat(pacemaker.getCurrentView()).isEqualByComparingTo(View.of(1L));
		newView = pacemaker.processQC(View.of(0L));
		assertThat(newView).isEmpty();
		assertThat(pacemaker.getCurrentView()).isEqualByComparingTo(View.of(1L));
		assertThat(pacemaker.getCurrentView()).isEqualByComparingTo(View.of(1L));
	}
}