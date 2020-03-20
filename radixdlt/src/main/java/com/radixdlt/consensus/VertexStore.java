/*
 *  (C) Copyright 2020 Radix DLT Ltd
 *
 *  Radix DLT Ltd licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except in
 *  compliance with the License.  You may obtain a copy of the
 *  License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 *  either express or implied.  See the License for the specific
 *  language governing permissions and limitations under the License.
 */

package com.radixdlt.consensus;

import com.radixdlt.crypto.Hash;
import com.radixdlt.engine.RadixEngine;
import com.radixdlt.engine.RadixEngineException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Manages the BFT Vertex chain
 */
public final class VertexStore {

	private final RadixEngine engine;
	private final Map<Hash, Vertex> vertices = new HashMap<>();
	private final Map<Hash, Vertex> committedVertices = new HashMap<>();
	private QuorumCertificate highestQC;

	// TODO: Cleanup this interface
	public VertexStore(
		Vertex genesisVertex,
		QuorumCertificate rootQC,
		RadixEngine engine
	) throws RadixEngineException {
		Objects.requireNonNull(genesisVertex);
		Objects.requireNonNull(rootQC);
		Objects.requireNonNull(engine);

		this.engine = engine;
		this.highestQC = rootQC;
		this.engine.store(genesisVertex.getAtom());
		this.vertices.put(genesisVertex.getId(), genesisVertex);
		this.committedVertices.put(genesisVertex.getId(), genesisVertex);
	}

	public void syncToQC(QuorumCertificate qc) {
		if (qc == null) {
			return;
		}

		if (highestQC == null || highestQC.getView().compareTo(qc.getView()) < 0) {
			highestQC = qc;
		}
	}

	public void insertVertex(Vertex vertex) throws VertexInsertionException {
		final Vertex parent = vertices.get(vertex.getParentId());
		if (parent == null) {
			throw new MissingParentException(vertex.getParentId());
		}

		this.syncToQC(vertex.getQC());

		if (vertex.getAtom() != null) {
			try {
				this.engine.store(vertex.getAtom());
			} catch (RadixEngineException e) {
				throw new VertexInsertionException("Failed to execute", e);
			}
		}

		vertices.put(vertex.getId(), vertex);
	}

	public Vertex commitVertex(Hash vertexId) {
		Vertex vertex = vertices.get(vertexId);
		if (vertex == null) {
			throw new IllegalStateException("Committing a vertex which was never inserted: " + vertexId);
		}

		committedVertices.put(vertexId, vertex);
		return vertex;
	}

	public List<Vertex> getPathFromRoot(Hash vertexId) {
		final List<Vertex> path = new ArrayList<>();

		Vertex vertex = vertices.get(vertexId);
		while (vertex != null && !committedVertices.containsKey(vertex.getId())) {
			path.add(vertex);
			vertex = vertices.get(vertex.getParentId());
		}

		return path;
	}

	public Vertex getVertex(Hash vertexId) {
		return vertices.get(vertexId);
	}

	public QuorumCertificate getHighestQC() {
		return this.highestQC;
	}
}
