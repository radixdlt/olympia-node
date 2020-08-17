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
import static org.mockito.Mockito.when;

import com.radixdlt.consensus.Vertex;
import com.radixdlt.consensus.VertexMetadata;
import com.radixdlt.consensus.bft.BFTValidatorSet;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.engine.RadixEngine;
import com.radixdlt.engine.RadixEngineException;
import com.radixdlt.identifiers.AID;
import com.radixdlt.middleware2.ClientAtom;
import com.radixdlt.middleware2.CommittedAtom;
import com.radixdlt.middleware2.LedgerAtom;
import com.radixdlt.middleware2.store.CommittedAtomsStore;
import java.util.function.Function;
import org.junit.Before;
import org.junit.Test;

public class RadixEngineStateComputerTest {
	private RadixEngineStateComputer executor;
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
		this.executor = new RadixEngineStateComputer(
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
		assertThat(executor.prepare(vertex)).contains(validatorSet);
	}

	@Test
	public void when_execute_vertex_with_engine_exception__then_is_available_on_query() throws RadixEngineException {
		CommittedAtom committedAtom = mock(CommittedAtom.class);
		when(committedAtom.getClientAtom()).thenReturn(mock(ClientAtom.class));
		AID aid = mock(AID.class);
		when(committedAtom.getAID()).thenReturn(aid);
		VertexMetadata vertexMetadata = mock(VertexMetadata.class);
		when(vertexMetadata.getView()).then(i -> View.of(50));
		when(vertexMetadata.getStateVersion()).then(i -> 1L);
		when(committedAtom.getVertexMetadata()).thenReturn(vertexMetadata);


		RadixEngineException e = mock(RadixEngineException.class);
		doThrow(e).when(radixEngine).checkAndStore(eq(committedAtom));
		executor.commit(committedAtom);

		assertThat(executor.getCommittedAtoms(0, 1)).contains(
			committedAtom
		);
	}

}