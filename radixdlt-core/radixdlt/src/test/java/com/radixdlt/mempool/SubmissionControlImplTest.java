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
import com.radixdlt.crypto.Hasher;
import com.radixdlt.mempool.SubmissionControlImpl.SubmissionControlSender;
import com.radixdlt.middleware2.ClientAtom;
import com.radixdlt.middleware2.LedgerAtom;
import com.radixdlt.serialization.DeserializeException;
import org.junit.Before;
import org.junit.Test;
import org.mockito.stubbing.Answer;
import com.radixdlt.engine.RadixEngine;
import com.radixdlt.serialization.Serialization;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static org.hamcrest.Matchers.*;

public class SubmissionControlImplTest {

	private Mempool mempool;
	private RadixEngine<LedgerAtom> radixEngine;
	private Serialization serialization;
	private SubmissionControlSender sender;
	private SubmissionControlImpl submissionControl;
	private Hasher hasher;

	@Before
	public void setUp() {
		this.mempool = throwingMock(Mempool.class);
		// No type check issues with mocking generic here
		@SuppressWarnings("unchecked")
		RadixEngine<LedgerAtom> re = throwingMock(RadixEngine.class);
		this.radixEngine = re;
		this.serialization = mock(Serialization.class);
		this.sender = mock(SubmissionControlSender.class);
		this.submissionControl = new SubmissionControlImpl(this.mempool, this.radixEngine, this.serialization, this.sender);
	}

	@Test
	public void when_command_deserialization_succeeds__then_command_submitted() throws Exception {
		Command command = new Command(new byte[] {});
		ClientAtom clientAtom = mock(ClientAtom.class);
		when(serialization.fromDson(any(), eq(ClientAtom.class))).thenReturn(clientAtom);
		when(serialization.toDson(eq(clientAtom), any())).thenReturn(new byte[] {});
		doNothing().when(mempool).add(any());
		doNothing().when(radixEngine).staticCheck(any());
		submissionControl.submitCommand(command);
		verify(radixEngine, times(1)).staticCheck(eq(clientAtom));
	}

	@Test
	public void when_command_deserialization_fails__then_throw_exception() throws Exception {
		Command command = new Command(new byte[] {});
		when(serialization.fromDson(any(), eq(ClientAtom.class))).thenThrow(new DeserializeException(""));
		assertThatThrownBy(() -> submissionControl.submitCommand(command))
			.isInstanceOf(MempoolRejectedException.class);
	}

	@Test
	public void sensible_tostring() {
		String tostring = this.submissionControl.toString();
		assertThat(tostring, containsString(this.submissionControl.getClass().getSimpleName()));
	}

    private static <T> T throwingMock(Class<T> classToMock) {
    	return mock(classToMock, illegalStateAnswer());
    }


	private static <T> Answer<T> illegalStateAnswer() {
		return inv -> {
			throw new IllegalStateException("Called unstubbed method");
		};
	}
}
