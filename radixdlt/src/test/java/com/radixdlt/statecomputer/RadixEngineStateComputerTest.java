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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
import java.util.function.Function;
import org.junit.Before;
import org.junit.Test;

public class RadixEngineStateComputerTest {
	private RadixEngineStateComputer stateComputer;
	private CommittedAtomsStore committedAtomsStore;
	private RadixEngine<LedgerAtom> radixEngine;
	private View epochHighView;
	private Function<Long, BFTValidatorSet> validatorSetMapping;

	@Before
	public void setup() {
		this.radixEngine = mock(RadixEngine.class);
		this.committedAtomsStore = mock(CommittedAtomsStore.class);
		this.epochHighView = View.of(100);
		// No issues with type checking for mock
		@SuppressWarnings("unchecked") Function<Long, BFTValidatorSet> vsm = mock(Function.class);
		this.validatorSetMapping = vsm;
		this.stateComputer = new RadixEngineStateComputer(
			radixEngine,
			validatorSetMapping,
			epochHighView,
			committedAtomsStore
		);
	}

	@Test
	public void when_compute_vertex_metadata_equal_to_high_view__then_should_return_true() {
		Vertex vertex = mock(Vertex.class);
		when(vertex.getView()).thenReturn(epochHighView);
		BFTValidatorSet validatorSet = mock(BFTValidatorSet.class);
		when(validatorSetMapping.apply(any())).thenReturn(validatorSet);
		assertThat(stateComputer.prepare(vertex)).contains(validatorSet);
	}

	@Test
	public void when_execute_vertex_with_null_command__then_is_available_on_query() throws RadixEngineException {
		ClientAtom committedAtom = mock(ClientAtom.class);
		AID aid = mock(AID.class);
		when(committedAtom.getAID()).thenReturn(aid);
		VertexMetadata vertexMetadata = mock(VertexMetadata.class);
		when(vertexMetadata.getView()).then(i -> View.of(50));
		when(vertexMetadata.getStateVersion()).then(i -> 1L);
		when(vertexMetadata.isEndOfEpoch()).thenReturn(true);

		RadixEngineException e = mock(RadixEngineException.class);
		doThrow(e).when(radixEngine).checkAndStore(any());
		stateComputer.commit(null, vertexMetadata);

		assertThat(stateComputer.getCommittedAtoms(0, 1))
			.hasOnlyOneElementSatisfying(c -> {
				assertThat(c.getClientAtom()).isNull();
				assertThat(c.getVertexMetadata()).isEqualTo(vertexMetadata);
			});
	}

}