package com.radixdlt.submission;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

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
import com.radixdlt.constraintmachine.CMError;
import com.radixdlt.constraintmachine.DataPointer;
import com.radixdlt.engine.RadixEngine;
import com.radixdlt.mempool.Mempool;
import com.radixdlt.mempool.MempoolDuplicateException;
import com.radixdlt.mempool.MempoolFullException;
import com.radixdlt.serialization.Serialization;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static org.hamcrest.Matchers.is;

public class SubmissionControlTest {

	private Mempool mempool;
	private RadixEngine radixEngine;
	private Serialization serialization;
	private Events events;

	private SubmissionControl submissionControl;


	@Before
	public void setUp() {
		this.mempool = mock(Mempool.class);
		this.radixEngine = mock(RadixEngine.class);
		this.serialization = mock(Serialization.class);
		this.events = mock(Events.class);

		// test module to hook up dependencies
		Module testModule = new AbstractModule() {
			@Override
			protected void configure() {
				bind(Mempool.class).toInstance(mempool);
				bind(RadixEngine.class).toInstance(radixEngine);
				bind(Serialization.class).toInstance(serialization);
				bind(Events.class).toInstance(events);
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
}
