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

package com.radixdlt.utils;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.reactivex.rxjava3.observers.TestObserver;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class ScheduledSenderToRxTest {

	private ScheduledSenderToRx<Object> scheduledSenderToRx;
	private ScheduledExecutorService executorService;
	private ScheduledExecutorService executorServiceMock;

	@Before
	public void setUp() {
		this.executorService = Executors.newSingleThreadScheduledExecutor(ThreadFactories.daemonThreads("TimeoutSender"));
		this.executorServiceMock = mock(ScheduledExecutorService.class);
		doAnswer(invocation -> {
			// schedule submissions with a small timeout to ensure that control is returned before the
			// "scheduled" runnable is executed, otherwise required events may not be triggered in time
			this.executorService.schedule((Runnable) invocation.getArguments()[0], 10, TimeUnit.MILLISECONDS);
			return null;
		}).when(this.executorServiceMock).schedule(any(Runnable.class), anyLong(), any());

		this.scheduledSenderToRx = new ScheduledSenderToRx<>(this.executorServiceMock);
	}

	@After
	public void tearDown() throws InterruptedException {
		if (this.executorService != null) {
			this.executorService.shutdown();
			this.executorService.awaitTermination(10L, TimeUnit.SECONDS);
		}
	}

	@Test
	public void when_subscribed_to_local_timeouts_and_schedule_timeout_twice__then_two_timeout_events_are_emitted() {
		TestObserver<Object> testObserver = scheduledSenderToRx.messages().test();
		Object o1 = mock(Object.class);
		Object o2 = mock(Object.class);
		long timeout = 10;
		scheduledSenderToRx.scheduleSend(o1, timeout);
		scheduledSenderToRx.scheduleSend(o2, timeout);
		testObserver.awaitCount(2);
		testObserver.assertNotComplete();
		testObserver.assertValues(o1, o2);
		verify(executorServiceMock, times(2)).schedule(any(Runnable.class), eq(timeout), eq(TimeUnit.MILLISECONDS));
	}
}
