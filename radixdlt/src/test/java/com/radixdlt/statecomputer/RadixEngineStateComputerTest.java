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
import com.radixdlt.consensus.Command;
import com.radixdlt.consensus.Vertex;
import com.radixdlt.consensus.VertexMetadata;
import com.radixdlt.consensus.bft.BFTValidatorSet;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.engine.RadixEngine;
import com.radixdlt.engine.RadixEngineException;
import com.radixdlt.identifiers.AID;
import com.radixdlt.middleware2.ClientAtom;
import com.radixdlt.middleware2.LedgerAtom;
import com.radixdlt.middleware2.store.CommittedAtomsStore;
import com.radixdlt.serialization.Serialization;
import com.radixdlt.serialization.SerializationException;
import com.radixdlt.statecomputer.RadixEngineStateComputer.CommittedAtomSender;
import com.radixdlt.utils.TypedMocks;

import java.util.function.Function;
import org.junit.Before;
import org.junit.Test;

public class RadixEngineStateComputerTest {
	private Serialization serialization;
	private RadixEngineStateComputer stateComputer;
	private CommittedAtomsStore committedAtomsStore;
	private RadixEngine<LedgerAtom> radixEngine;
	private View epochHighView;
	private Function<Long, BFTValidatorSet> validatorSetMapping;
	private CommittedAtomSender committedAtomSender;

	@Before
	public void setup() {
		this.serialization = mock(Serialization.class);
		this.radixEngine = TypedMocks.rmock(RadixEngine.class);
		this.committedAtomsStore = mock(CommittedAtomsStore.class);
		this.epochHighView = View.of(100);
		// No issues with type checking for mock
		@SuppressWarnings("unchecked") Function<Long, BFTValidatorSet> vsm = mock(Function.class);
		this.committedAtomSender = mock(CommittedAtomSender.class);
		this.validatorSetMapping = vsm;
		this.stateComputer = new RadixEngineStateComputer(
			serialization,
			radixEngine,
			validatorSetMapping,
			epochHighView,
			committedAtomsStore,
			committedAtomSender
		);
	}

	@Test
	public void when_prepare_vertex_metadata_equal_to_high_view__then_should_return_validator_set() {
		Vertex vertex = mock(Vertex.class);
		when(vertex.getView()).thenReturn(epochHighView);
		BFTValidatorSet validatorSet = mock(BFTValidatorSet.class);
		when(validatorSetMapping.apply(any())).thenReturn(validatorSet);
		//assertThat(stateComputer.prepare(vertex)).contains(validatorSet);
		assertThat(stateComputer.prepare(vertex)).isTrue();
	}

	@Test
	public void when_execute_vertex_with_command__then_is_stored_in_engine() throws Exception {
		ClientAtom clientAtom = mock(ClientAtom.class);
		AID aid = mock(AID.class);
		when(clientAtom.getAID()).thenReturn(aid);
		VertexMetadata vertexMetadata = mock(VertexMetadata.class);
		when(vertexMetadata.getView()).then(i -> View.of(50));
		when(vertexMetadata.getStateVersion()).then(i -> 1L);
		when(vertexMetadata.isEndOfEpoch()).thenReturn(true);

		when(serialization.fromDson(any(), eq(ClientAtom.class))).thenReturn(clientAtom);

		BFTValidatorSet validatorSet = mock(BFTValidatorSet.class);
		when(validatorSetMapping.apply(any())).thenReturn(validatorSet);

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
		when(vertexMetadata.getStateVersion()).then(i -> 1L);
		when(vertexMetadata.isEndOfEpoch()).thenReturn(true);

		BFTValidatorSet validatorSet = mock(BFTValidatorSet.class);
		when(validatorSetMapping.apply(any())).thenReturn(validatorSet);

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
		when(vertexMetadata.getStateVersion()).then(i -> 1L);
		when(vertexMetadata.isEndOfEpoch()).thenReturn(true);
		BFTValidatorSet validatorSet = mock(BFTValidatorSet.class);
		when(validatorSetMapping.apply(any())).thenReturn(validatorSet);

		stateComputer.commit(null, vertexMetadata);

		assertThat(stateComputer.getCommittedCommands(0, 1))
			.hasOnlyOneElementSatisfying(c -> {
				assertThat(c.getCommand()).isNull();
				assertThat(c.getVertexMetadata()).isEqualTo(vertexMetadata);
			});
	}

	@Test
	public void when_execute_vertex_with_malformed_command__then_is_available_on_query() throws SerializationException {
		VertexMetadata vertexMetadata = mock(VertexMetadata.class);
		when(vertexMetadata.getView()).then(i -> View.of(50));
		when(vertexMetadata.getStateVersion()).then(i -> 1L);
		when(vertexMetadata.isEndOfEpoch()).thenReturn(true);
		BFTValidatorSet validatorSet = mock(BFTValidatorSet.class);
		when(validatorSetMapping.apply(any())).thenReturn(validatorSet);

		when(serialization.fromDson(any(), eq(ClientAtom.class))).thenThrow(new SerializationException(""));

		stateComputer.commit(new Command(new byte[] {0, 1}), vertexMetadata);

		assertThat(stateComputer.getCommittedCommands(0, 1))
			.hasOnlyOneElementSatisfying(c -> {
				assertThat(c.getCommand()).isNull();
				assertThat(c.getVertexMetadata()).isEqualTo(vertexMetadata);
			});
	}


}