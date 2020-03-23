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

package com.radixdlt.consensus;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.mock;

import com.radixdlt.crypto.ECDSASignatures;
import com.radixdlt.engine.RadixEngine;
import io.reactivex.rxjava3.observers.TestObserver;
import org.junit.Test;

public class VertexStoreTest {
	@Test
	public void when_insert_and_commit_vertex__then_committed_vertex_should_emit() throws Exception {
		Vertex genesisVertex = Vertex.createGenesis(null);
		VertexMetadata vertexMetadata = new VertexMetadata(
			View.genesis(), genesisVertex.getId(), null, null
		);
		QuorumCertificate rootQC = new QuorumCertificate(vertexMetadata, new ECDSASignatures());
		RadixEngine radixEngine = mock(RadixEngine.class);
		VertexStore vertexStore = new VertexStore(genesisVertex, rootQC, radixEngine);

		Vertex nextVertex = Vertex.createVertex(rootQC, View.of(1), null);
		vertexStore.insertVertex(nextVertex);

		TestObserver<Vertex> testObserver = TestObserver.create();
		vertexStore.lastCommittedVertex().subscribe(testObserver);
		testObserver.awaitCount(1);

		assertThat(vertexStore.commitVertex(nextVertex.getId())).isEqualTo(nextVertex);
		testObserver.awaitCount(2);
		testObserver.assertValues(genesisVertex, nextVertex);
	}
}