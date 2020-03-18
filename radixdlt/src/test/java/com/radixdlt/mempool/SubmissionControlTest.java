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

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.stubbing.Answer;
import org.radix.atoms.events.AtomExceptionEvent;
import org.radix.events.Events;
import com.radixdlt.common.AID;
import com.radixdlt.common.Atom;
import com.radixdlt.constraintmachine.CMError;
import com.radixdlt.constraintmachine.DataPointer;
import com.radixdlt.engine.RadixEngine;
import com.radixdlt.serialization.Serialization;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static org.hamcrest.Matchers.*;

public class SubmissionControlTest {

	private Mempool mempool;
	private RadixEngine radixEngine;
	private Serialization serialization;
	private Events events;

	private SubmissionControl submissionControl;

	@Before
	public void setUp() {
		this.mempool = throwingMock(Mempool.class);
		this.radixEngine = throwingMock(RadixEngine.class);
		this.serialization = throwingMock(Serialization.class);
		this.events = throwingMock(Events.class);
		this.submissionControl = new SubmissionControlImpl(this.mempool, this.radixEngine, this.serialization, this.events);
	}

	@Test
	public void when_radix_engine_returns_error__then_event_is_broadcast()
		throws MempoolFullException, MempoolDuplicateException {
		CMError cmError = throwingMock(CMError.class);
		doReturn("dummy").when(cmError).getErrMsg();
		doReturn("dummy error").when(cmError).getErrorDescription();
		doReturn(DataPointer.ofAtom()).when(cmError).getDataPointer();
		Optional<CMError> error = Optional.of(cmError);
		doReturn(error).when(this.radixEngine).staticCheck(any());
		doNothing().when(this.events).broadcast(any());

		Atom atom = throwingMock(Atom.class);
		doReturn(AID.ZERO).when(atom).getAID();

		this.submissionControl.submitAtom(atom);

		verify(this.events, times(1)).broadcast(ArgumentMatchers.any(AtomExceptionEvent.class));
		verify(this.mempool, never()).addAtom(any());
	}

	@Test
	public void when_radix_engine_returns_ok__then_atom_is_added_to_mempool()
		throws MempoolFullException, MempoolDuplicateException {
		doReturn(Optional.empty()).when(this.radixEngine).staticCheck(any());
		doNothing().when(this.mempool).addAtom(any());

		this.submissionControl.submitAtom(throwingMock(Atom.class));

		verify(this.events, never()).broadcast(any());
		verify(this.mempool, times(1)).addAtom(ArgumentMatchers.any(Atom.class));
	}

	@Test
	public void if_deserialisation_fails__then_callback_is_not_called()
		throws MempoolFullException, MempoolDuplicateException {
		doReturn(Optional.empty()).when(this.radixEngine).staticCheck(any());
		doThrow(new IllegalArgumentException()).when(this.serialization).fromJsonObject(any(), any());

		AtomicBoolean called = new AtomicBoolean(false);

		try {
			this.submissionControl.submitAtom(mock(JSONObject.class, illegalStateAnswer()), a -> called.set(true));
			fail();
		} catch (IllegalArgumentException e) {
			assertThat(called.get(), is(false));
			verify(this.events, never()).broadcast(any());
			verify(this.mempool, never()).addAtom(any());
		}
	}

	@Test
	public void after_json_deserialised__then_callback_is_called_and_aid_returned()
		throws MempoolFullException, MempoolDuplicateException {
		doReturn(Optional.empty()).when(this.radixEngine).staticCheck(any());
		Atom atomMock = throwingMock(Atom.class);
		doReturn(AID.ZERO).when(atomMock).getAID();
		doReturn(atomMock).when(this.serialization).fromJsonObject(any(), any());
		doNothing().when(this.mempool).addAtom(any());

		AtomicBoolean called = new AtomicBoolean(false);

		AID result = this.submissionControl.submitAtom(throwingMock(JSONObject.class), a -> called.set(true));

		assertSame(AID.ZERO, result);

		assertThat(called.get(), is(true));
		verify(this.events, never()).broadcast(any());
		verify(this.mempool, times(1)).addAtom(any());
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
