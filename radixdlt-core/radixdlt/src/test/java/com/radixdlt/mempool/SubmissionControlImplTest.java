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

import com.radixdlt.atommodel.system.SystemParticle;
import com.radixdlt.consensus.Command;
import com.radixdlt.consensus.Sha256Hasher;
import com.radixdlt.crypto.Hasher;
import com.radixdlt.engine.RadixEngineException;
import com.radixdlt.mempool.SubmissionControlImpl.SubmissionControlSender;
import com.radixdlt.middleware.ParticleGroup;
import com.radixdlt.middleware.SpunParticle;
import com.radixdlt.middleware2.ClientAtom;
import com.radixdlt.middleware2.LedgerAtom;
import com.radixdlt.serialization.DeserializeException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.mockito.stubbing.Answer;
import com.radixdlt.atommodel.Atom;
import com.radixdlt.constraintmachine.DataPointer;
import com.radixdlt.engine.RadixEngine;
import com.radixdlt.serialization.Serialization;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

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
		this.hasher = Sha256Hasher.withDefaultSerialization();
		this.submissionControl = new SubmissionControlImpl(this.mempool, this.radixEngine, this.serialization, this.sender, this.hasher);
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
	public void when_radix_engine_returns_error__then_event_is_broadcast() throws Exception {
		RadixEngineException e = mock(RadixEngineException.class);
		when(e.getDataPointer()).thenReturn(DataPointer.ofAtom());
		doThrow(e).when(this.radixEngine).staticCheck(any());

		ClientAtom atom = mock(ClientAtom.class);
		this.submissionControl.submitAtom(atom);

		verify(this.sender, times(1)).sendRadixEngineFailure(any(), any());
		verify(this.mempool, never()).add(any());
	}

	@Test
	public void when_radix_engine_returns_ok__then_atom_is_added_to_mempool() throws Exception {
		doNothing().when(this.radixEngine).staticCheck(any());
		doNothing().when(this.mempool).add(any());

		ClientAtom atom = mock(ClientAtom.class);
		when(this.serialization.toDson(eq(atom), any())).thenReturn(new byte[] {});
		this.submissionControl.submitAtom(atom);

		verify(this.sender, never()).sendRadixEngineFailure(any(), any());
		verify(this.sender, never()).sendDeserializeFailure(any(), any());
		verify(this.mempool, times(1)).add(any());
	}

	@Test
	public void if_deserialisation_fails__then_callback_is_not_called() throws Exception {
		doThrow(new IllegalArgumentException()).when(this.serialization).fromJsonObject(any(), any());

		AtomicBoolean called = new AtomicBoolean(false);

		try {
			this.submissionControl.submitAtom(mock(JSONObject.class, illegalStateAnswer()), a -> called.set(true));
			fail();
		} catch (IllegalArgumentException e) {
			assertThat(called.get()).isFalse();
			verify(this.sender, never()).sendDeserializeFailure(any(), any());
			verify(this.sender, never()).sendRadixEngineFailure(any(), any());
			verify(this.mempool, never()).add(any());
		}
	}

	@Test
	public void after_json_deserialised__then_callback_is_called_and_aid_returned()
		throws Exception {
		doNothing().when(this.radixEngine).staticCheck(any());
		Atom atom = new Atom();
		atom.addParticleGroup(
			ParticleGroup.of(
				SpunParticle.up(new SystemParticle(0, 0, 0))
			)
		);
		doReturn(atom).when(this.serialization).fromJsonObject(any(), any());
		doNothing().when(this.mempool).add(any());
		when(serialization.toDson(any(), any())).thenReturn(new byte[] {0, 1, 2, 3});
		// No type check issues with mocking generic here
		@SuppressWarnings("unchecked")
		Consumer<ClientAtom> callback = mock(Consumer.class);
		this.submissionControl.submitAtom(throwingMock(JSONObject.class), callback);

		verify(callback, times(1)).accept(any());
		verify(this.sender, never()).sendRadixEngineFailure(any(), any());
		verify(this.sender, never()).sendDeserializeFailure(any(), any());
		verify(this.mempool, times(1)).add(any());
	}

	@Test
	public void sensible_tostring() {
		String tostring = this.submissionControl.toString();
		assertThat(tostring).contains(this.submissionControl.getClass().getSimpleName());
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
