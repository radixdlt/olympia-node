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

package com.radixdlt.mempool;

import com.radixdlt.consensus.Command;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import io.reactivex.rxjava3.processors.PublishProcessor;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class MempoolReceiverTest {

	private PublishProcessor<Command> commands;

	private SubmissionControl submissionControl;
	private MempoolReceiver mempoolReceiver;

	@Before
	public void setUp() {
		this.commands = PublishProcessor.create();
		MempoolNetworkRx mempoolRx = mock(MempoolNetworkRx.class);
		doReturn(this.commands).when(mempoolRx).commands();
		this.submissionControl = mock(SubmissionControl.class);
		this.mempoolReceiver = new MempoolReceiver(mempoolRx, this.submissionControl);
	}

	@After
	public void tearDown() {
		this.mempoolReceiver.stop();
	}

	@Test
	public void testStart() {
		assertFalse(this.mempoolReceiver.running());
		this.mempoolReceiver.start();
		assertTrue(this.mempoolReceiver.running());
	}

	@Test
	public void testStop() {
		assertFalse(this.mempoolReceiver.running());
		this.mempoolReceiver.start();
		assertTrue(this.mempoolReceiver.running());
		this.mempoolReceiver.start();
		assertTrue(this.mempoolReceiver.running()); // Still started
		this.mempoolReceiver.stop();
		assertFalse(this.mempoolReceiver.running());
	}

	@Test
	public void testThroughput() throws MempoolRejectedException, InterruptedException {
		Semaphore completed = new Semaphore(0);
		doAnswer(inv -> {
			completed.release();
			return null;
		}).when(this.submissionControl).submitCommand(any());

		this.mempoolReceiver.start();
		assertTrue(this.mempoolReceiver.running());

		Command command = mock(Command.class);
		commands.onNext(command);

		// Wait for everything to get pushed through the pipeline
		assertTrue(completed.tryAcquire(10, TimeUnit.SECONDS));

		verify(this.submissionControl, times(1)).submitCommand(any());
	}
}
