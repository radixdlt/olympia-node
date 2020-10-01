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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.radixdlt.consensus.Command;
import com.radixdlt.consensus.LedgerHeader;
import com.radixdlt.consensus.VerifiedLedgerHeaderAndProof;
import com.radixdlt.consensus.bft.BFTValidatorSet;
import com.radixdlt.consensus.bft.VerifiedVertex;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.constraintmachine.CMInstruction;
import com.radixdlt.engine.RadixEngine;
import com.radixdlt.engine.RadixEngine.RadixEngineBranch;
import com.radixdlt.engine.RadixEngineException;
import com.radixdlt.identifiers.AID;
import com.radixdlt.ledger.AccumulatorState;
import com.radixdlt.ledger.DtoLedgerHeaderAndProof;
import com.radixdlt.ledger.VerifiedCommandsAndProof;
import com.radixdlt.middleware2.ClientAtom;
import com.radixdlt.middleware2.LedgerAtom;
import com.radixdlt.middleware2.store.CommittedAtomsStore;
import com.radixdlt.serialization.DeserializeException;
import com.radixdlt.serialization.Serialization;
import com.radixdlt.statecomputer.RadixEngineStateComputer.CommittedAtomSender;
import com.radixdlt.utils.TypedMocks;

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
	public void when_execute_vertex_with_command__then_is_stored_in_engine() throws Exception {
		ClientAtom clientAtom = mock(ClientAtom.class);
		CMInstruction cmInstruction = mock(CMInstruction.class);
		when(cmInstruction.getMicroInstructions()).thenReturn(ImmutableList.of());
		when(clientAtom.getCMInstruction()).thenReturn(cmInstruction);
		AID aid = mock(AID.class);
		when(clientAtom.getAID()).thenReturn(aid);
		when(serialization.fromDson(any(), eq(ClientAtom.class))).thenReturn(clientAtom);

		VerifiedLedgerHeaderAndProof proof = mock(VerifiedLedgerHeaderAndProof.class);
		AccumulatorState accumulatorState = mock(AccumulatorState.class);
		when(accumulatorState.getStateVersion()).thenReturn(1L);
		when(proof.getAccumulatorState()).thenReturn(accumulatorState);
		when(proof.isEndOfEpoch()).thenReturn(false);
		VerifiedCommandsAndProof command = mock(VerifiedCommandsAndProof.class);
		when(command.getHeader()).thenReturn(proof);
		when(command.getCommands()).thenReturn(ImmutableList.of(mock(Command.class)));

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

		VerifiedLedgerHeaderAndProof proof = mock(VerifiedLedgerHeaderAndProof.class);
		AccumulatorState accumulatorState = mock(AccumulatorState.class);
		when(accumulatorState.getStateVersion()).thenReturn(1L);
		when(proof.getAccumulatorState()).thenReturn(accumulatorState);
		when(proof.isEndOfEpoch()).thenReturn(false);

		VerifiedCommandsAndProof committedCommand = mock(VerifiedCommandsAndProof.class);
		when(committedCommand.getHeader()).thenReturn(proof);
		when(committedCommand.getCommands()).thenReturn(ImmutableList.of(mock(Command.class)));

		stateComputer.commit(committedCommand);

		DtoLedgerHeaderAndProof start = mock(DtoLedgerHeaderAndProof.class);
		LedgerHeader ledgerHeader = mock(LedgerHeader.class);
		AccumulatorState accumulatorState1 = mock(AccumulatorState.class);
		when(accumulatorState1.getStateVersion()).thenReturn(0L);
		when(ledgerHeader.getAccumulatorState()).thenReturn(accumulatorState1);
		when(start.getLedgerHeader()).thenReturn(ledgerHeader);

		VerifiedCommandsAndProof commands = stateComputer.getNextCommittedCommands(start, 1);
		assertThat(commands).isNotNull();
		assertThat(commands.getHeader()).isEqualTo(proof);
	}

	@Test
	public void when_commit_vertex_with_malformed_command__then_is_available_on_query()
		throws DeserializeException {
		when(serialization.fromDson(any(), eq(ClientAtom.class))).thenThrow(new DeserializeException(""));

		Command cmd = new Command(new byte[] {0, 1});
		VerifiedLedgerHeaderAndProof proof = mock(VerifiedLedgerHeaderAndProof.class);
		AccumulatorState accumulatorState1 = mock(AccumulatorState.class);
		when(accumulatorState1.getStateVersion()).thenReturn(1L);
		when(proof.getAccumulatorState()).thenReturn(accumulatorState1);
		when(proof.isEndOfEpoch()).thenReturn(false);

		VerifiedCommandsAndProof commandsAndProof = mock(VerifiedCommandsAndProof.class);
		when(commandsAndProof.getHeader()).thenReturn(proof);
		when(commandsAndProof.getCommands()).thenReturn(ImmutableList.of(cmd));

		stateComputer.commit(commandsAndProof);
		DtoLedgerHeaderAndProof start = mock(DtoLedgerHeaderAndProof.class);
		LedgerHeader ledgerHeader = mock(LedgerHeader.class);
		AccumulatorState accumulatorState = mock(AccumulatorState.class);
		when(accumulatorState.getStateVersion()).thenReturn(0L);
		when(ledgerHeader.getAccumulatorState()).thenReturn(accumulatorState);
		when(start.getLedgerHeader()).thenReturn(ledgerHeader);
		VerifiedCommandsAndProof commands = stateComputer.getNextCommittedCommands(start, 1);
		assertThat(commands).isNotNull();
		assertThat(commands.getHeader()).isEqualTo(proof);
	}
}