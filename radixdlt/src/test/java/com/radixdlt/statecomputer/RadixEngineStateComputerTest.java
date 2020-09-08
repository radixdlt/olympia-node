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
import com.radixdlt.consensus.PreparedCommand;
import com.radixdlt.consensus.Vertex;
import com.radixdlt.consensus.VertexMetadata;
import com.radixdlt.consensus.bft.BFTValidatorSet;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.constraintmachine.CMInstruction;
import com.radixdlt.engine.RadixEngine;
import com.radixdlt.engine.RadixEngineException;
import com.radixdlt.identifiers.AID;
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

		VertexMetadata vertexMetadata = mock(VertexMetadata.class);
		when(vertexMetadata.getView()).then(i -> View.of(50));
		PreparedCommand preparedCommand = mock(PreparedCommand.class);
		when(preparedCommand.getStateVersion()).thenReturn(1L);
		when(preparedCommand.isEndOfEpoch()).thenReturn(false);
		when(vertexMetadata.getPreparedCommand()).thenReturn(preparedCommand);

		when(serialization.fromDson(any(), eq(ClientAtom.class))).thenReturn(clientAtom);

		Command command = mock(Command.class);
		stateComputer.commit(command, vertexMetadata);
		verify(radixEngine, times(1)).checkAndStore(any());
		verify(committedAtomSender, never()).sendCommittedAtom(any());
	}

	@Test
	public void when_execute_vertex_with_exception__then_is_available_for_query() throws Exception {
		ClientAtom clientAtom = mock(ClientAtom.class);
		AID aid = mock(AID.class);
		when(clientAtom.getAID()).thenReturn(aid);
		VertexMetadata vertexMetadata = mock(VertexMetadata.class);
		when(vertexMetadata.getView()).then(i -> View.of(50));
		PreparedCommand preparedCommand = mock(PreparedCommand.class);
		when(preparedCommand.getStateVersion()).thenReturn(1L);
		when(preparedCommand.isEndOfEpoch()).thenReturn(false);
		when(vertexMetadata.getPreparedCommand()).thenReturn(preparedCommand);

		when(serialization.fromDson(any(), eq(ClientAtom.class))).thenReturn(clientAtom);
		RadixEngineException e = mock(RadixEngineException.class);
		doThrow(e).when(radixEngine).checkAndStore(any());

		Command command = mock(Command.class);
		stateComputer.commit(command, vertexMetadata);

		assertThat(stateComputer.getCommittedCommands(0, 1))
			.hasOnlyOneElementSatisfying(c -> {
				assertThat(c.getCommand()).isEqualTo(command);
				assertThat(c.getVertexMetadata()).isEqualTo(vertexMetadata);
			});
	}

	@Test
	public void when_execute_vertex_is_end_of_epoch_with_null_command__then_is_available_on_query() {
		ClientAtom committedAtom = mock(ClientAtom.class);
		AID aid = mock(AID.class);
		when(committedAtom.getAID()).thenReturn(aid);
		VertexMetadata vertexMetadata = mock(VertexMetadata.class);
		when(vertexMetadata.getView()).then(i -> View.of(50));
		PreparedCommand preparedCommand = mock(PreparedCommand.class);
		when(preparedCommand.getStateVersion()).thenReturn(1L);
		when(preparedCommand.isEndOfEpoch()).thenReturn(true);
		when(vertexMetadata.getPreparedCommand()).thenReturn(preparedCommand);

		RadixEngineValidatorSetBuilder validatorSetBuilder = mock(RadixEngineValidatorSetBuilder.class);
		when(radixEngine.getComputedState(eq(RadixEngineValidatorSetBuilder.class)))
			.thenReturn(validatorSetBuilder);
		when(validatorSetBuilder.build()).thenReturn(mock(BFTValidatorSet.class));

		stateComputer.commit(null, vertexMetadata);

		assertThat(stateComputer.getCommittedCommands(0, 1))
			.hasOnlyOneElementSatisfying(c -> {
				assertThat(c.getCommand()).isNull();
				assertThat(c.getVertexMetadata()).isEqualTo(vertexMetadata);
			});
	}

	@Test
	public void when_commit_vertex_with_malformed_command__then_is_available_on_query() throws DeserializeException {
		VertexMetadata vertexMetadata = mock(VertexMetadata.class);
		when(vertexMetadata.getView()).then(i -> View.of(50));
		PreparedCommand preparedCommand = mock(PreparedCommand.class);
		when(preparedCommand.getStateVersion()).thenReturn(1L);
		when(preparedCommand.isEndOfEpoch()).thenReturn(false);
		when(vertexMetadata.getPreparedCommand()).thenReturn(preparedCommand);

		when(serialization.fromDson(any(), eq(ClientAtom.class))).thenThrow(new DeserializeException(""));

		Command cmd = new Command(new byte[] {0, 1});
		assertThat(stateComputer.commit(cmd, vertexMetadata)).isEmpty();
		assertThat(stateComputer.getCommittedCommands(0, 1))
			.hasOnlyOneElementSatisfying(c -> {
				assertThat(c.getCommand()).isEqualTo(cmd);
				assertThat(c.getVertexMetadata()).isEqualTo(vertexMetadata);
			});
	}
}