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

package com.radixdlt.statecomputer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.radixdlt.consensus.Command;
import com.radixdlt.consensus.LedgerState;
import com.radixdlt.consensus.VerifiedCommittedHeader;
import com.radixdlt.consensus.Vertex;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.constraintmachine.CMInstruction;
import com.radixdlt.engine.RadixEngine;
import com.radixdlt.engine.RadixEngineException;
import com.radixdlt.identifiers.AID;
import com.radixdlt.ledger.VerifiedCommittedCommands;
import com.radixdlt.middleware2.ClientAtom;
import com.radixdlt.middleware2.LedgerAtom;
import com.radixdlt.middleware2.store.CommittedAtomsStore;
import com.radixdlt.serialization.Serialization;
import com.radixdlt.serialization.SerializationException;
import com.radixdlt.statecomputer.RadixEngineStateComputer.CommittedAtomSender;
import com.radixdlt.store.berkeley.NextCommittedLimitReachedException;
import com.radixdlt.utils.TypedMocks;

import java.util.function.BiConsumer;
import org.junit.Before;
import org.junit.Test;

public class RadixEngineStateComputerTest {
	private Serialization serialization;
	private RadixEngineStateComputer stateComputer;
	private CommittedAtomsStore committedAtomsStore;
	private RadixEngine<LedgerAtom> radixEngine;
	private View epochHighView;
	private CommittedAtomSender committedAtomSender;

	@Before
	public void setup() {
		this.serialization = mock(Serialization.class);
		this.radixEngine = TypedMocks.rmock(RadixEngine.class);
		this.committedAtomsStore = mock(CommittedAtomsStore.class);
		this.epochHighView = View.of(100);
		this.committedAtomSender = mock(CommittedAtomSender.class);
		this.stateComputer = new RadixEngineStateComputer(
			serialization,
			radixEngine,
			epochHighView,
			committedAtomsStore,
			committedAtomSender
		);
	}

	@Test
	public void when_prepare_vertex_metadata_equal_to_high_view__then_should_return_epoch_change() {
		Vertex vertex = mock(Vertex.class);
		when(vertex.getView()).thenReturn(epochHighView);
		assertThat(stateComputer.prepare(vertex)).isTrue();
	}

	@Test
	public void when_prepare_vertex_metadata_lower_to_high_view__then_should_return_not_epoch_change() {
		Vertex vertex = mock(Vertex.class);
		when(vertex.getView()).thenReturn(epochHighView.previous());
		assertThat(stateComputer.prepare(vertex)).isFalse();
	}

	@Test
	public void when_execute_vertex_with_command__then_is_stored_in_engine() throws Exception {
		ClientAtom clientAtom = mock(ClientAtom.class);
		CMInstruction cmInstruction = mock(CMInstruction.class);
		when(cmInstruction.getMicroInstructions()).thenReturn(ImmutableList.of());
		when(clientAtom.getCMInstruction()).thenReturn(cmInstruction);
		AID aid = mock(AID.class);
		when(clientAtom.getAID()).thenReturn(aid);

		when(serialization.fromDson(any(), eq(ClientAtom.class))).thenReturn(clientAtom);

		VerifiedCommittedHeader proof = mock(VerifiedCommittedHeader.class);
		LedgerState ledgerState = mock(LedgerState.class);
		when(ledgerState.getStateVersion()).thenReturn(1L);
		when(ledgerState.isEndOfEpoch()).thenReturn(false);
		when(proof.getLedgerState()).thenReturn(ledgerState);
		VerifiedCommittedCommands command = mock(VerifiedCommittedCommands.class);
		when(command.getProof()).thenReturn(proof);
		doAnswer(invocation -> {
			BiConsumer<Long, Command> consumer = invocation.getArgument(0);
			consumer.accept(1L, mock(Command.class));
			return null;
		}).when(command).forEach(any());

		stateComputer.commit(command);
		verify(radixEngine, times(1)).checkAndStore(any());
		verify(committedAtomSender, never()).sendCommittedAtom(any());
	}

	@Test
	public void when_execute_vertex_with_exception__then_is_available_for_query() throws Exception {
		ClientAtom clientAtom = mock(ClientAtom.class);
		AID aid = mock(AID.class);
		when(clientAtom.getAID()).thenReturn(aid);

		when(serialization.fromDson(any(), eq(ClientAtom.class))).thenReturn(clientAtom);
		RadixEngineException e = mock(RadixEngineException.class);
		doThrow(e).when(radixEngine).checkAndStore(any());

		VerifiedCommittedHeader proof = mock(VerifiedCommittedHeader.class);
		LedgerState ledgerState = mock(LedgerState.class);
		when(ledgerState.getStateVersion()).thenReturn(1L);
		when(ledgerState.isEndOfEpoch()).thenReturn(false);
		when(proof.getLedgerState()).thenReturn(ledgerState);

		VerifiedCommittedCommands committedCommand = mock(VerifiedCommittedCommands.class);
		when(committedCommand.getProof()).thenReturn(proof);
		doAnswer(invocation -> {
			BiConsumer<Long, Command> consumer = invocation.getArgument(0);
			consumer.accept(1L, mock(Command.class));
			return null;
		}).when(committedCommand).forEach(any());

		stateComputer.commit(committedCommand);

		VerifiedCommittedCommands commands = stateComputer.getNextCommittedCommands(0, 1);
		assertThat(commands).isNotNull();
		assertThat(commands.getProof()).isEqualTo(proof);
	}

	@Test
	public void when_commit_vertex_with_malformed_command__then_is_available_on_query()
		throws SerializationException, NextCommittedLimitReachedException {
		when(serialization.fromDson(any(), eq(ClientAtom.class))).thenThrow(new SerializationException(""));

		Command cmd = new Command(new byte[] {0, 1});
		VerifiedCommittedHeader proof = mock(VerifiedCommittedHeader.class);
		LedgerState ledgerState = mock(LedgerState.class);
		when(ledgerState.getStateVersion()).thenReturn(1L);
		when(ledgerState.isEndOfEpoch()).thenReturn(false);
		when(proof.getLedgerState()).thenReturn(ledgerState);

		VerifiedCommittedCommands command = mock(VerifiedCommittedCommands.class);
		when(command.getProof()).thenReturn(proof);
		doAnswer(invocation -> {
			BiConsumer<Long, Command> consumer = invocation.getArgument(0);
			consumer.accept(1L, cmd);
			return null;
		}).when(command).forEach(any());

		assertThat(stateComputer.commit(command)).isEmpty();
		VerifiedCommittedCommands commands = stateComputer.getNextCommittedCommands(0, 1);
		assertThat(commands).isNotNull();
		assertThat(commands.getProof()).isEqualTo(proof);
	}
}