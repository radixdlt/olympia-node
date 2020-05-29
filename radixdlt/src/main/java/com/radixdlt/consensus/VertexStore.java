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
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.crypto.Hash;

import com.radixdlt.middleware2.CommittedAtom;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.subjects.BehaviorSubject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import javax.annotation.Nullable;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Manages the BFT Vertex chain.
 *
 * In general this class is NOT thread-safe except for getVertices() and getHighestQC().
 * TODO: make thread-safe
 */
public final class VertexStore {
	private static final Logger log = LogManager.getLogger();

	public interface GetVerticesRequest {
		Hash getVertexId();
		int getCount();
	}

	public interface SyncSender {
		void synced(Hash vertexId);
	}

	private final BehaviorSubject<Vertex> lastCommittedVertex = BehaviorSubject.create();
	private final SyncSender syncSender;
	private final SyncVerticesRPCSender syncVerticesRPCSender;
	private final SyncedStateComputer<CommittedAtom> syncedStateComputer;
	private final SystemCounters counters;
	private final Object lock = new Object();

	// These should never be empty
	private final AtomicReference<Hash> rootId = new AtomicReference<>();
	private final AtomicReference<QuorumCertificate> highestQC = new AtomicReference<>();
	private final AtomicReference<QuorumCertificate> highestCommittedQC = new AtomicReference<>();

	private final Map<Hash, Vertex> vertices = new ConcurrentHashMap<>();
	private final Map<Hash, SyncState> syncing = new ConcurrentHashMap<>();

	// TODO: Cleanup this interface
	public VertexStore(
		Vertex genesisVertex,
		QuorumCertificate rootQC,
		SyncedStateComputer<CommittedAtom> syncedStateComputer,
		SyncVerticesRPCSender syncVerticesRPCSender,
		SyncSender syncSender,
		SystemCounters counters
	) {
		this.syncedStateComputer = syncedStateComputer;
		this.syncVerticesRPCSender = syncVerticesRPCSender;
		this.syncSender = syncSender;
		this.counters = Objects.requireNonNull(counters);
		this.highestQC.set(Objects.requireNonNull(rootQC));
		this.highestCommittedQC.set(rootQC);

		if (genesisVertex.getAtom() != null) {
			CommittedAtom committedGenesis = genesisVertex.getAtom().committed(rootQC.getProposed());
			syncedStateComputer.execute(committedGenesis);
		}

		this.rootId.set(genesisVertex.getId());
		this.vertices.put(genesisVertex.getId(), genesisVertex);
		this.lastCommittedVertex.onNext(genesisVertex);
	}

	private static class SyncState {
		private final QuorumCertificate qc;
		private final ECPublicKey author;
		private final LinkedList<Vertex> fetched = new LinkedList<>();

		SyncState(QuorumCertificate qc, ECPublicKey author) {
			this.qc = qc;
			this.author = author;
		}
	}

	public void processGetVerticesRequest(GetVerticesRequest request) {
		log.info("SYNC_VERTICES: Received GetVerticesRequest {}", request);
		List<Vertex> fetched = this.getVertices(request.getVertexId(), request.getCount());
		log.info("SYNC_VERTICES: Sending Response {}", fetched);
		this.syncVerticesRPCSender.sendGetVerticesResponse(request, fetched);
	}

	public void processGetVerticesResponse(GetVerticesResponse response) {
		log.info("SYNC_VERTICES: Received GetVerticesResponse {}", response);

		final Hash syncTo = response.getOpaque(Hash.class);
		SyncState syncState = syncing.get(syncTo);
		if (syncState == null) {
			return;
		}

		final QuorumCertificate qc = syncState.qc;

		if (response.getVertices().isEmpty()) {
			log.info("GET_VERTICES failed: {}", qc);
			// failed
			return;
		}

		Vertex vertex = response.getVertices().get(0);
		syncState.fetched.addFirst(vertex);
		Hash nextVertexId = vertex.getQC().getProposed().getId();

		if (vertices.containsKey(nextVertexId)) {
			for (Vertex v: syncState.fetched) {
				if (!addQC(v.getQC())) {
					log.info("GET_VERTICES failed: {}", qc);
					return;
				}
				try {
					insertVertex(v);
				} catch (VertexInsertionException e) {
					log.info("GET_VERTICES failed: {}", e.getMessage());
					return;
				}
			}
			addQC(qc);
		} else {
			log.info("SYNC_VERTICES: Sending further GetVerticesRequest {}", nextVertexId);
			syncVerticesRPCSender.sendGetVerticesRequest(nextVertexId, syncState.author, 1, syncTo);
		}
	}

	private void doSync(QuorumCertificate qc, ECPublicKey author) {
		final Hash vertexId = qc.getProposed().getId();
		if (syncing.containsKey(vertexId)) {
			return;
		}

		SyncState syncState = new SyncState(qc, author);
		syncing.put(vertexId, syncState);

		log.info("SYNC_VERTICES: Sending initial GetVerticesRequest {}", vertexId);
		syncVerticesRPCSender.sendGetVerticesRequest(vertexId, syncState.author, 1, vertexId);
	}

	public void processLocalSync(Hash vertexId) {
		syncing.remove(vertexId);
	}

	public boolean syncToQC(QuorumCertificate qc, QuorumCertificate committedQC, @Nullable ECPublicKey author) {
		if (addQC(qc)) {
			return true;
		}

		if (author != null) {
			this.doSync(qc, author);
		}


		/*
		Optional<VertexMetadata> committed = committedQC.getCommitted();
		if (!committed.isPresent()) {
			return false;
		}

		VertexMetadata committedMetadata = committed.get();
		if (root.getView().compareTo(committedMetadata.getView()) < 0) {

			long stateVersion = committed.get().getStateVersion();
			final Hash hash;
			try {
				// TODO: Make it easier to retrieve signatures of QC
				hash = Hash.of(DefaultSerialization.getInstance().toDson(committedQC.getVoteData(), Output.HASH));
			} catch (SerializationException e) {
				throw new IllegalStateException("Failed to serialize");
			}				List<ECPublicKey> signers = committedQC.getSignatures().signedMessage(hash);

			Observable.combineLatest(
				vertexSupplier.sendGetVerticesRequest(committedQC.getProposed().getId(), author, 3).toObservable(),
				syncedStateComputer.syncTo(stateVersion, signers).toSingleDefault(0).toObservable(),
				(v, l) -> v
			).subscribe(vertices -> {
				synchronized (lock) {
					if (root.getView().compareTo(committedMetadata.getView()) < 0) {
						uncommitted.clear();
						for (Vertex v : vertices) {
							if (!v.getId().equals(committedMetadata.getId())) {
								uncommitted.put(v.getId(), v);
							}
						}
						this.root = committedMetadata;
						this.highestCommittedQC = committedQC;
						this.highestQC = committedQC;
					}
				}
			});
		}
		*/

		return false;
	}

	private boolean addQC(QuorumCertificate qc) {
		synchronized (lock) {
			if (!vertices.containsKey(qc.getProposed().getId())) {
				return false;
			}

			if (highestQC.get().getView().compareTo(qc.getView()) < 0) {
				highestQC.set(qc);
			}

			QuorumCertificate highestCommitted = highestCommittedQC.get();
			qc.getCommitted().ifPresent(vertexMetadata -> {
				if (!highestCommitted.getCommitted().isPresent()
					|| highestCommitted.getCommitted().get().getStateVersion() < vertexMetadata.getStateVersion()) {
					this.highestCommittedQC.set(qc);
				}
			});

			return true;
		}
	}

	public void insertVertex(Vertex vertex) throws VertexInsertionException {
		synchronized (lock) {
			if (!vertices.containsKey(vertex.getParentId())) {
				throw new MissingParentException(vertex.getParentId());
			}

			// TODO: Don't check for state computer errors for now so that we don't
			// TODO: have to deal with failing leader proposals
			// TODO: Reinstate this when ProposalGenerator + Mempool can guarantee correct proposals
			// TODO: (also see commitVertex->storeAtom)

			vertices.put(vertex.getId(), vertex);
			updateVertexStoreSize();

			if (syncing.containsKey(vertex.getId())) {
				syncSender.synced(vertex.getId());
			}
		}
	}

	// TODO: add signature proof
	public Vertex commitVertex(VertexMetadata commitMetadata) {
		synchronized (lock) {
			final Hash vertexId = commitMetadata.getId();
			final Vertex tipVertex = vertices.get(vertexId);
			if (tipVertex == null) {
				throw new IllegalStateException("Committing a vertex which was never inserted: " + vertexId);
			}
			final LinkedList<Vertex> path = new LinkedList<>();
			Vertex vertex = tipVertex;
			while (vertex != null && !rootId.get().equals(vertex.getId())) {
				path.addFirst(vertex);
				vertex = vertices.remove(vertex.getParentId());
			}

			for (Vertex committed : path) {
				if (committed.getAtom() != null) {
					CommittedAtom committedAtom = committed.getAtom().committed(commitMetadata);
					this.counters.increment(CounterType.CONSENSUS_PROCESSED);
					syncedStateComputer.execute(committedAtom);
				}

				lastCommittedVertex.onNext(committed);
			}

			rootId.set(commitMetadata.getId());

			updateVertexStoreSize();
			return tipVertex;
		}
	}

	public Observable<Vertex> lastCommittedVertex() {
		return lastCommittedVertex;
	}

	public List<Vertex> getPathFromRoot(Hash vertexId) {
		final List<Vertex> path = new ArrayList<>();

		Vertex vertex = vertices.get(vertexId);
		while (vertex != null && !vertex.getId().equals(rootId.get())) {
			path.add(vertex);
			vertex = vertices.get(vertex.getParentId());
		}

		return path;
	}

	/**
	 * Retrieves the highest committed qc in the store
	 * @return the highest committed qc
	 */
	public QuorumCertificate getHighestCommittedQC() {
		return this.highestCommittedQC.get();
	}

	/**
	 * Retrieves the highest qc in the store
	 * Thread-safe.
	 *
	 * @return the highest quorum certificate
	 */
	public QuorumCertificate getHighestQC() {
		return this.highestQC.get();
	}

	/**
	 * Retrieves the vertex with the given vertexId if it exists in the store.
	 * Thread-safe.
	 *
	 * @param vertexId the id of the vertex
	 * @param count the number of verticies to retrieve
	 * @return the vertex or null, if it is not stored
	 */
	private List<Vertex> getVertices(Hash vertexId, int count) {
		Hash nextId = vertexId;
		List<Vertex> response = new ArrayList<>(count);
		for (int i = 0; i < count; i++) {
			Vertex vertex = this.vertices.get(nextId);
			if (vertex == null) {
				return Collections.emptyList();
			}

			response.add(vertex);
			nextId = vertex.getParentId();
		}

		return response;
	}

	public void clearSyncs() {
		syncing.clear();
	}

	public int getSize() {
		return vertices.size();
	}

	private void updateVertexStoreSize() {
		this.counters.set(CounterType.CONSENSUS_VERTEXSTORE_SIZE, this.vertices.size());
	}
}
