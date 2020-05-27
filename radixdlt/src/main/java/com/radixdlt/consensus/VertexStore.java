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

import com.radixdlt.DefaultSerialization;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.counters.SystemCounters.CounterType;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.crypto.Hash;

import com.radixdlt.middleware2.CommittedAtom;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.SerializationException;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.subjects.BehaviorSubject;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages the BFT Vertex chain.
 *
 * In general this class is NOT thread-safe except for getVertex() and getHighestQC().
 * TODO: make thread-safe
 */
public final class VertexStore {
	private final SyncedStateComputer<CommittedAtom> syncedStateComputer;
	private final SystemCounters counters;
	private final Map<Hash, Vertex> uncommitted = new ConcurrentHashMap<>();
	private final BehaviorSubject<Vertex> lastCommittedVertex = BehaviorSubject.create();

	// Should never be null
	private VertexMetadata root;

	// Should never be null
	private volatile QuorumCertificate highestQC;
	private volatile QuorumCertificate highestCommittedQC;

	// TODO: Cleanup this interface
	public VertexStore(
		Vertex genesisVertex,
		QuorumCertificate rootQC,
		SyncedStateComputer<CommittedAtom> syncedStateComputer,
		SystemCounters counters
	) {
		this.syncedStateComputer = syncedStateComputer;
		this.counters = Objects.requireNonNull(counters);
		this.highestQC = Objects.requireNonNull(rootQC);
		this.highestCommittedQC = rootQC;

		if (genesisVertex.getAtom() != null) {
			CommittedAtom committedGenesis = genesisVertex.getAtom().committed(rootQC.getProposed());
			syncedStateComputer.execute(committedGenesis);
		}

		this.root = VertexMetadata.ofVertex(genesisVertex);
		this.lastCommittedVertex.onNext(genesisVertex);
	}

	public boolean syncToQC(QuorumCertificate qc) {
		return this.syncToQC(qc, this.getHighestCommittedQC());
	}

	public boolean syncToQC(QuorumCertificate qc, QuorumCertificate committedQC) {
		final Vertex vertex = uncommitted.get(qc.getProposed().getId());
		if (vertex != null || qc.getProposed().getId().equals(root.getId())) {
			addQC(qc);
			return true;
		}

		Optional<VertexMetadata> committed = committedQC.getCommitted();
		if (committed.isPresent()) {
			long stateVersion = committed.get().getStateVersion();
			try {
				// TODO: Make it easier to retrieve signatures of QC
				Hash hash = Hash.of(DefaultSerialization.getInstance().toDson(committedQC.getVoteData(), Output.HASH));
				List<ECPublicKey> signers = committedQC.getSignatures().signedMessage(hash);
				if (!syncedStateComputer.syncTo(stateVersion, signers)) {
					return false;
				}
			} catch (SerializationException e) {
				throw new IllegalStateException("Failed to serialize");
			}
		}

		return false;
	}

	public void addQC(QuorumCertificate qc) {
		if (highestQC.getView().compareTo(qc.getView()) < 0) {
			highestQC = qc;
		}

		qc.getCommitted().ifPresent(vertexMetadata -> {
			if (!highestCommittedQC.getCommitted().isPresent()
				|| highestCommittedQC.getCommitted().get().getStateVersion() < vertexMetadata.getStateVersion()) {
				this.highestCommittedQC = qc;
			}
		});
	}

	public void insertVertex(Vertex vertex) throws VertexInsertionException {
		final Vertex parent = uncommitted.get(vertex.getParentId());
		if (parent == null && !vertex.getParentId().equals(root.getId())) {
			throw new MissingParentException(vertex.getParentId());
		}

		// TODO: Don't check for state computer errors for now so that we don't
		// TODO: have to deal with failing leader proposals
		// TODO: Reinstate this when ProposalGenerator + Mempool can guarantee correct proposals
		// TODO: (also see commitVertex->storeAtom)

		uncommitted.put(vertex.getId(), vertex);
		updateVertexStoreSize();
	}

	// TODO: add signature proof
	public Vertex commitVertex(VertexMetadata commitMetadata) {
		final Hash vertexId = commitMetadata.getId();
		final Vertex tipVertex = uncommitted.get(vertexId);
		if (tipVertex == null) {
			throw new IllegalStateException("Committing a vertex which was never inserted: " + vertexId);
		}
		final LinkedList<Vertex> path = new LinkedList<>();
		Vertex vertex = tipVertex;
		while (vertex != null && !root.getId().equals(vertex.getId())) {
			path.addFirst(vertex);
			vertex = uncommitted.remove(vertex.getParentId());
		}

		for (Vertex committed : path) {
			if (committed.getAtom() != null) {
				CommittedAtom committedAtom = committed.getAtom().committed(commitMetadata);
				this.counters.increment(CounterType.CONSENSUS_PROCESSED);
				syncedStateComputer.execute(committedAtom);
			}

			lastCommittedVertex.onNext(committed);
		}

		root = commitMetadata;

		updateVertexStoreSize();
		return tipVertex;
	}

	public Observable<Vertex> lastCommittedVertex() {
		return lastCommittedVertex;
	}

	public List<Vertex> getPathFromRoot(Hash vertexId) {
		final List<Vertex> path = new ArrayList<>();

		Vertex vertex = uncommitted.get(vertexId);
		while (vertex != null) {
			path.add(vertex);
			vertex = uncommitted.get(vertex.getParentId());
		}

		return path;
	}

	/**
	 * Retrieves the highest committed qc in the store
	 * @return the highest committed qc
	 */
	public QuorumCertificate getHighestCommittedQC() {
		return this.highestCommittedQC;
	}

	/**
	 * Retrieves the highest qc in the store
	 * Thread-safe.
	 *
	 * @return the highest quorum certificate
	 */
	public QuorumCertificate getHighestQC() {
		return this.highestQC;
	}

	/**
	 * Retrieves the vertex with the given vertexId if it exists in the store.
	 * Thread-safe.
	 *
	 * @param vertexId the id of the vertex
	 * @return the vertex or null, if it is not stored
	 */
	public Vertex getVertex(Hash vertexId) {
		return this.uncommitted.get(vertexId);
	}

	public int getSize() {
		return uncommitted.size();
	}

	private void updateVertexStoreSize() {
		this.counters.set(CounterType.CONSENSUS_VERTEXSTORE_SIZE, this.uncommitted.size());
	}
}
