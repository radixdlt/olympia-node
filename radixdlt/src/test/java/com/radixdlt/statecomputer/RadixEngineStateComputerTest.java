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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.radixdlt.consensus.Command;
import com.radixdlt.consensus.VerifiedLedgerHeaderAndProof;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.constraintmachine.CMInstruction;
import com.radixdlt.engine.RadixEngine;
import com.radixdlt.identifiers.AID;
import com.radixdlt.ledger.AccumulatorState;
import com.radixdlt.ledger.VerifiedCommandsAndProof;
import com.radixdlt.middleware2.ClientAtom;
import com.radixdlt.middleware2.LedgerAtom;
import com.radixdlt.middleware2.store.CommittedAtomsStore;
import com.radixdlt.serialization.Serialization;
import com.radixdlt.utils.TypedMocks;

import org.junit.Before;
import org.junit.Test;

public class RadixEngineStateComputerTest {
	private Serialization serialization;
	private RadixEngineStateComputer stateComputer;
	private CommittedAtomsStore committedAtomsStore;
	private RadixEngine<LedgerAtom> radixEngine;
	private View epochHighView;

	@Before
	public void setup() {
		this.serialization = mock(Serialization.class);
		this.radixEngine = TypedMocks.rmock(RadixEngine.class);
		this.committedAtomsStore = mock(CommittedAtomsStore.class);
		this.epochHighView = View.of(100);
		this.stateComputer = new RadixEngineStateComputer(
			serialization,
			radixEngine,
			epochHighView,
			committedAtomsStore
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
	}
}