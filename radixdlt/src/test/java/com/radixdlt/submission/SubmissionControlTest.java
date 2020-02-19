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

package com.radixdlt.submission;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentMatchers;
import org.radix.atoms.events.AtomExceptionEvent;
import org.radix.events.Events;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.radixdlt.common.AID;
import com.radixdlt.common.Atom;
import com.radixdlt.consensus.MempoolSubmissionCallback;
import com.radixdlt.consensus.NetworkRx;
import com.radixdlt.constraintmachine.CMError;
import com.radixdlt.constraintmachine.DataPointer;
import com.radixdlt.engine.RadixEngine;
import com.radixdlt.mempool.Mempool;
import com.radixdlt.mempool.MempoolDuplicateException;
import com.radixdlt.mempool.MempoolFullException;
import com.radixdlt.serialization.Serialization;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static org.hamcrest.Matchers.*;

public class SubmissionControlTest {

	private Mempool mempool;
	private RadixEngine radixEngine;
	private Serialization serialization;
	private Events events;
	private NetworkRx networkRx;
	private AtomicReference<MempoolSubmissionCallback> rxCallback = new AtomicReference<>();

	private SubmissionControl submissionControl;


	@Before
	public void setUp() {
		this.mempool = mock(Mempool.class);
		this.radixEngine = mock(RadixEngine.class);
		this.serialization = mock(Serialization.class);
		this.events = mock(Events.class);
		this.networkRx = mock(NetworkRx.class);

		doAnswer(args -> {
			rxCallback.set(args.getArgument(0));
			return null;
		}).when(this.networkRx).addMempoolSubmissionCallback(any());

		// test module to hook up dependencies
		Module testModule = new AbstractModule() {
			@Override
			protected void configure() {
				bind(Mempool.class).toInstance(mempool);
				bind(RadixEngine.class).toInstance(radixEngine);
				bind(Serialization.class).toInstance(serialization);
				bind(Events.class).toInstance(events);
				bind(NetworkRx.class).toInstance(networkRx);
			}
		};

		Injector injector = Guice.createInjector(testModule, new SubmissionControlModule());

		this.submissionControl = injector.getInstance(SubmissionControl.class);
	}

	@Test
	public void when_radix_engine_returns_error__then_event_is_broadcast()
		throws MempoolFullException, MempoolDuplicateException {
		CMError cmError = mock(CMError.class);
		when(cmError.getErrMsg()).thenReturn("dummy");
		when(cmError.getDataPointer()).thenReturn(DataPointer.ofAtom());
		Optional<CMError> error = Optional.of(cmError);
		when(this.radixEngine.staticCheck(any())).thenReturn(error);

		this.submissionControl.submitAtom(mock(Atom.class));

		verify(this.events, times(1)).broadcast(ArgumentMatchers.any(AtomExceptionEvent.class));
		verify(this.mempool, never()).addAtom(any());
	}

	@Test
	public void when_radix_engine_returns_ok__then_atom_is_added_to_mempool()
		throws MempoolFullException, MempoolDuplicateException {
		when(this.radixEngine.staticCheck(any())).thenReturn(Optional.empty());

		this.submissionControl.submitAtom(mock(Atom.class));

		verify(this.events, never()).broadcast(any());
		verify(this.mempool, times(1)).addAtom(ArgumentMatchers.any(Atom.class));
	}

	@Test
	public void if_deserialisation_fails__then_callback_is_not_called()
		throws MempoolFullException, MempoolDuplicateException {
		when(this.radixEngine.staticCheck(any())).thenReturn(Optional.empty());
		when(this.serialization.fromJsonObject(any(), any())).thenThrow(new IllegalArgumentException());

		AtomicBoolean called = new AtomicBoolean(false);

		try {
			this.submissionControl.submitAtom(mock(JSONObject.class), a -> called.set(true));
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
		when(this.radixEngine.staticCheck(any())).thenReturn(Optional.empty());
		Atom atomMock = mock(Atom.class);
		when(atomMock.getAID()).thenReturn(AID.ZERO);
		when(this.serialization.fromJsonObject(any(), any())).thenReturn(atomMock);

		AtomicBoolean called = new AtomicBoolean(false);

		AID result = this.submissionControl.submitAtom(mock(JSONObject.class), a -> called.set(true));

		assertSame(AID.ZERO, result);

		assertThat(called.get(), is(true));
		verify(this.events, never()).broadcast(any());
		verify(this.mempool, times(1)).addAtom(any());
	}

	@Test
	public void when_receiving_atom__then_atom_is_submitted()
		throws MempoolFullException, MempoolDuplicateException {
		assertNotNull(rxCallback.get());
		Atom atomMock = mock(Atom.class);
		when(atomMock.getAID()).thenReturn(AID.ZERO);

		rxCallback.get().accept(atomMock);
		verify(this.mempool, times(1)).addAtom(any());
	}

	@Test
	public void when_receiving_duplicate_atom__handled_correctly()
		throws MempoolFullException, MempoolDuplicateException {
		assertNotNull(rxCallback.get());
		Atom atomMock = mock(Atom.class);
		when(atomMock.getAID()).thenReturn(AID.ZERO);
		doThrow(new MempoolDuplicateException(atomMock, "fake duplicate")).when(this.mempool).addAtom(any());

		rxCallback.get().accept(atomMock);
		verify(this.mempool, times(1)).addAtom(any());
	}

	@Test
	public void sensible_tostring() {
		String tostring = this.submissionControl.toString();
		assertThat(tostring, containsString(this.submissionControl.getClass().getSimpleName()));
	}
}
