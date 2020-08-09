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

package com.radixdlt.execution;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.radixdlt.consensus.VertexMetadata;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.engine.RadixEngine;
import com.radixdlt.engine.RadixEngineException;
import com.radixdlt.execution.RadixEngineExecutor.RadixEngineExecutorEventSender;
import com.radixdlt.identifiers.AID;
import com.radixdlt.middleware2.ClientAtom;
import com.radixdlt.middleware2.CommittedAtom;
import com.radixdlt.middleware2.LedgerAtom;
import com.radixdlt.middleware2.store.CommittedAtomsStore;
import org.junit.Before;
import org.junit.Test;

public class RadixEngineExecutorTest {
	private RadixEngineExecutor executor;
	private CommittedAtomsStore committedAtomsStore;
	private RadixEngine<LedgerAtom> radixEngine;
	private RadixEngineExecutorEventSender sender;

	@Before
	public void setup() {
		this.radixEngine = mock(RadixEngine.class);
		this.committedAtomsStore = mock(CommittedAtomsStore.class);
		this.sender = mock(RadixEngineExecutorEventSender.class);
		this.executor = new RadixEngineExecutor(
			radixEngine,
			committedAtomsStore,
			sender
		);
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
		executor.execute(committedAtom);

		assertThat(executor.getCommittedAtoms(0, 1)).contains(
			committedAtom
		);
	}

}