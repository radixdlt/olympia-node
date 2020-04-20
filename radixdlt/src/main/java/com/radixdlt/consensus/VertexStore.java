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

import com.radixdlt.counters.SystemCounters;
import com.radixdlt.counters.SystemCounters.CounterType;
import com.radixdlt.crypto.Hash;
import com.radixdlt.engine.RadixEngine;
import com.radixdlt.engine.RadixEngineException;

import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.subjects.BehaviorSubject;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages the BFT Vertex chain. NOT thread-safe.
 */
public final class VertexStore {

	private final RadixEngine engine;
	private final SystemCounters counters;

	private final Map<Hash, Vertex> vertices = new ConcurrentHashMap<>();
	private final BehaviorSubject<Vertex> lastCommittedVertex = BehaviorSubject.create();

	// Should never be null
	private Vertex root;

	// Should never be null
	private QuorumCertificate highestQC;

	// TODO: Cleanup this interface
	public VertexStore(
		Vertex genesisVertex,
		QuorumCertificate rootQC,
		RadixEngine engine,
		SystemCounters counters
	) {
		this.engine = Objects.requireNonNull(engine);
		this.counters = Objects.requireNonNull(counters);
		this.highestQC = Objects.requireNonNull(rootQC);
		try {
			this.engine.store(genesisVertex.getAtom());
		} catch (RadixEngineException e) {
			throw new IllegalStateException("Could not store genesis atom: " + genesisVertex.getAtom(), e);
		}
		this.vertices.put(genesisVertex.getId(), genesisVertex);
		this.root = genesisVertex;
		this.lastCommittedVertex.onNext(genesisVertex);
	}

	public void syncToQC(QuorumCertificate qc, VertexSupplier vertexSupplier) throws SyncException {
		final Vertex vertex = vertices.get(qc.getProposed().getId());
		if (vertex == null) {
			if (!vertices.containsKey(qc.getParent().getId())) {
				// Too far behind
				// TODO: more syncing
				throw new SyncException(qc);
			}

			final Vertex proposedVertex;
			try {
				// TODO: remove blocking
				proposedVertex = vertexSupplier.getVertex(qc.getProposed().getId()).blockingGet();
			} catch (Exception e) {
				throw new SyncException(qc, e);
			}

			try {
				this.insertVertex(proposedVertex);
			} catch (VertexInsertionException e) {
				// Currently only looking to sync one vertex away from known QC
				// so this should never throw the MissingParentException
				throw new IllegalStateException("Should not go here.", e);
			}
		}

		if (highestQC.getView().compareTo(qc.getView()) < 0) {
			highestQC = qc;
		}
	}

	public void insertVertex(Vertex vertex) throws VertexInsertionException {
		final Vertex parent = vertices.get(vertex.getParentId());
		if (parent == null) {
			throw new MissingParentException(vertex.getParentId());
		}

		if (vertex.getAtom() != null) {
			try {
				this.counters.increment(CounterType.LEDGER_PROCESSED);
				this.engine.store(vertex.getAtom());
				this.counters.increment(CounterType.LEDGER_STORED);
			} catch (RadixEngineException e) {
				// TODO: Don't check for state computer errors for now so that we don't
				// TODO: have to deal with failing leader proposals
				// TODO: Reinstate this when ProposalGenerator + Mempool can guarantee correct proposals
				//throw new VertexInsertionException("Failed to execute", e);
			}
		}

		vertices.put(vertex.getId(), vertex);
	}

	public Vertex commitVertex(Hash vertexId) {
		final Vertex tipVertex = vertices.get(vertexId);
		if (tipVertex == null) {
			throw new IllegalStateException("Committing a vertex which was never inserted: " + vertexId);
		}
		final LinkedList<Vertex> path = new LinkedList<>();
		Vertex vertex = tipVertex;
		while (vertex != null && !root.equals(vertex)) {
			path.addFirst(vertex);
			vertex = vertices.remove(vertex.getParentId());
		}

		for (Vertex committed : path) {
			lastCommittedVertex.onNext(committed);
		}

		vertices.remove(root.getId());
		root = tipVertex;

		return tipVertex;
	}

	public Observable<Vertex> lastCommittedVertex() {
		return lastCommittedVertex;
	}

	public List<Vertex> getPathFromRoot(Hash vertexId) {
		final List<Vertex> path = new ArrayList<>();

		Vertex vertex = vertices.get(vertexId);
		while (vertex != null && !vertex.getId().equals(root.getId())) {
			path.add(vertex);
			vertex = vertices.get(vertex.getParentId());
		}

		return path;
	}

	public QuorumCertificate getHighestQC() {
		return this.highestQC;
	}

	public int getSize() {
		return vertices.size();
	}
}
