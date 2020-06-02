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
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
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

	private enum SyncStage {
		PREPARING,
		GET_COMMITTED_VERTICES,
		SYNC_TO_COMMIT,
		GET_PREPARED_VERTICES
	}

	private static class SyncState {
		private final QuorumCertificate qc;
		private final QuorumCertificate committedQC;
		private final VertexMetadata committedVertexMetadata;
		private final ECPublicKey author;
		private SyncStage syncStage;
		private final LinkedList<Vertex> fetched = new LinkedList<>();

		SyncState(QuorumCertificate qc, QuorumCertificate committedQC, ECPublicKey author) {
			if (committedQC.getView().equals(View.genesis())) {
				this.committedVertexMetadata = committedQC.getProposed();
			} else {
				if (!committedQC.getCommitted().isPresent()) {
					throw new IllegalStateException("committedQC must have a commit");
				}
				this.committedVertexMetadata = committedQC.getCommitted().get();
			}

			this.qc = qc;
			this.committedQC = committedQC;
			this.author = author;
			this.syncStage = SyncStage.PREPARING;
		}

		public void setSyncStage(SyncStage syncStage) {
			this.syncStage = syncStage;
		}

		public QuorumCertificate getQC() {
			return qc;
		}

		public QuorumCertificate getCommittedQC() {
			return committedQC;
		}

		@Override
		public String toString() {
			return String.format("%s{qc=%s committedQC=%s syncState=%s}", this.getClass().getSimpleName(), qc, committedQC, syncStage);
		}
	}

	public void processGetVerticesRequest(GetVerticesRequest request) {
		log.info("SYNC_VERTICES: Received GetVerticesRequest {}", request);
		List<Vertex> fetched = this.getVertices(request.getVertexId(), request.getCount());
		log.info("SYNC_VERTICES: Sending Response {}", fetched);
		this.syncVerticesRPCSender.sendGetVerticesResponse(request, fetched);
	}

	private void rebuildStoreAndSyncQC(SyncState syncState) {
		log.info("SYNC_STATE: Rebuilding and syncing QC: sync={} curRoot={}", syncState, vertices.get(rootId.get()));

		syncState.fetched.sort(Comparator.comparing(Vertex::getView));

		// TODO: check if there are any vertices which haven't been local sync processed yet
		// TODO: cleanup syncs which have views lower than this
		this.vertices.clear();
		this.highestCommittedQC.set(syncState.committedQC);
		this.highestQC.set(syncState.committedQC);
		this.rootId.set(syncState.committedVertexMetadata.getId());

		for (Vertex vertex : syncState.fetched) {
			try {
				insertVertexInternal(vertex, this.vertices.isEmpty());
			} catch (VertexInsertionException e) {
				throw new IllegalStateException("Could not insert vertex " + vertex);
			}
		}

		this.syncToQC(syncState.qc, syncState.committedQC, syncState.author);
	}

	public void processCommittedStateSync(CommittedStateSync committedStateSync) {
		log.info("SYNC_STATE: synced {}", committedStateSync);

		Hash syncTo = committedStateSync.getOpaque(Hash.class);
		SyncState syncState = syncing.get(syncTo);
		if (syncState != null) {
			rebuildStoreAndSyncQC(syncState);
		}
	}

	private void processVerticesResponseForCommittedSync(Hash syncTo, SyncState syncState, GetVerticesResponse response) {
		log.info("SYNC_STATE: Processing vertices {}", syncState);

		long stateVersion = syncState.committedVertexMetadata.getStateVersion();
		List<ECPublicKey> signers = Collections.singletonList(syncState.author);
		syncState.fetched.addAll(response.getVertices());

		if (syncedStateComputer.syncTo(stateVersion, signers, syncTo)) {
			rebuildStoreAndSyncQC(syncState);
		} else {
			syncState.syncStage = SyncStage.SYNC_TO_COMMIT;
		}
	}

	private void processVerticesResponseForVerticesSync(Hash syncTo, SyncState syncState, GetVerticesResponse response) {
		Vertex vertex = response.getVertices().get(0);
		syncState.fetched.addFirst(vertex);
		Hash nextVertexId = vertex.getQC().getProposed().getId();

		if (vertices.containsKey(nextVertexId)) {
			for (Vertex v: syncState.fetched) {
				if (!addQC(v.getQC())) {
					log.info("GET_VERTICES failed: {}", syncState.qc);
					return;
				}
				try {
					insertVertexInternal(v, false);
				} catch (VertexInsertionException e) {
					log.info("GET_VERTICES failed: {}", e.getMessage());
					return;
				}
			}
			addQC(syncState.qc);
		} else {
			log.info("SYNC_VERTICES: Sending further GetVerticesRequest for qc={} fetched={} root={}",
				syncState.qc, syncState.fetched.size(), vertices.get(rootId.get()));
			syncVerticesRPCSender.sendGetVerticesRequest(nextVertexId, syncState.author, 1, syncTo);
		}
	}

	public void processGetVerticesResponse(GetVerticesResponse response) {
		log.info("SYNC_VERTICES: Received GetVerticesResponse {}", response);

		final Hash syncTo = response.getOpaque(Hash.class);
		SyncState syncState = syncing.get(syncTo);
		if (syncState == null) {
			return;
		}

		if (response.getVertices().isEmpty()) {
			log.info("GET_VERTICES failed: {}", syncState.qc);
			// failed
			// TODO: retry
			return;
		}

		switch (syncState.syncStage) {
			case GET_COMMITTED_VERTICES:
				processVerticesResponseForCommittedSync(syncTo, syncState, response);
				break;
			case GET_PREPARED_VERTICES:
				processVerticesResponseForVerticesSync(syncTo, syncState, response);
				break;
			default:
				throw new IllegalStateException("Unknown sync stage: " + syncState.syncStage);
		}
	}

	private void doSync(SyncState syncState) {
		final Hash vertexId = syncState.getQC().getProposed().getId();
		if (syncing.containsKey(vertexId)) {
			return;
		}

		syncState.setSyncStage(SyncStage.GET_PREPARED_VERTICES);
		syncing.put(vertexId, syncState);

		log.info("SYNC_VERTICES: Vertices: Sending initial GetVerticesRequest for qc={}", syncState.getQC());
		syncVerticesRPCSender.sendGetVerticesRequest(vertexId, syncState.author, 1, vertexId);
	}

	private void doCommittedSync(SyncState syncState) {
		final Hash vertexId = syncState.getCommittedQC().getProposed().getId();
		if (syncing.containsKey(vertexId)) {
			// TODO: what if current sync isn't for commit?
			return;
		}

		syncState.setSyncStage(SyncStage.GET_COMMITTED_VERTICES);
		syncing.put(vertexId, syncState);

		log.info("SYNC_VERTICES: Committed: Sending initial GetVerticesRequest {}", syncState.getCommittedQC());
		syncVerticesRPCSender.sendGetVerticesRequest(vertexId, syncState.author, 3, vertexId);
	}


	public void processLocalSync(Hash vertexId) {
		log.info("LOCAL_SYNC: Processed {}", vertexId);
		syncing.remove(vertexId);
	}

	public boolean syncToQC(QuorumCertificate qc, QuorumCertificate committedQC, @Nullable ECPublicKey author) {
		if (addQC(qc)) {
			return true;
		}

		log.info("SYNC_TO_QC: Need sync: {} {}", qc, committedQC);
		final SyncState syncState = new SyncState(qc, committedQC, author);
		final VertexMetadata committedMetadata = syncState.committedVertexMetadata;
		if (!vertices.containsKey(committedMetadata.getId())) {
			View rootView = vertices.get(rootId.get()).getView();
			if (rootView.compareTo(committedMetadata.getView()) < 0) {
				log.info("SYNC_TO_QC: Need committed sync: {} {} highestQC={} rootView={}", qc, committedMetadata, highestQC.get(), rootView);
				doCommittedSync(syncState);
				return false;
			}
		}

		if (author != null) {
			this.doSync(syncState);
		}

		return false;
	}

	private boolean addQC(QuorumCertificate qc) {
		if (!vertices.containsKey(qc.getProposed().getId())) {
			return false;
		}

		if (highestQC.get().getView().compareTo(qc.getView()) < 0) {
			highestQC.set(qc);
		}

		qc.getCommitted().ifPresent(vertexMetadata -> {
			QuorumCertificate highestCommitted = highestCommittedQC.get();
			Optional<VertexMetadata> highest = highestCommitted.getCommitted();
			if (highest.isPresent()) {
				if (highest.get().getView().compareTo(vertexMetadata.getView()) < 0) {
					this.highestCommittedQC.set(qc);
				}
			} else {
				this.highestCommittedQC.set(qc);
			}
		});


		return true;
	}

	private void insertVertexInternal(Vertex vertex, boolean force) throws VertexInsertionException {
		if (!force && !vertices.containsKey(vertex.getParentId())) {
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

	public void insertVertex(Vertex vertex) throws VertexInsertionException {
		insertVertexInternal(vertex, false);
	}

	/**
	 * Commit a vertex. Executes the atom and prunes the tree. Returns
	 * the Vertex if commit was successful. If the store is ahead of
	 * what is to be committed, returns an empty optional
	 *
	 * @param commitMetadata the metadata of the vertex to commit
	 * @return the vertex if sucessful, otherwise an empty optional if vertex was already committed
	 */
	public Optional<Vertex> commitVertex(VertexMetadata commitMetadata) {
		if (commitMetadata.getView().compareTo(vertices.get(rootId.get()).getView()) < 0) {
			return Optional.empty();
		}

		final Hash vertexId = commitMetadata.getId();
		final Vertex tipVertex = vertices.get(vertexId);
		if (tipVertex == null) {
			throw new IllegalStateException("Committing vertex not in store: " + commitMetadata);
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
		return Optional.of(tipVertex);
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
	 * Retrieves list of vertices starting with the given vertexId and
	 * then proceeding to its ancestors.
	 *
	 * if the store does not contain some vertex then will return an empty
	 * list.
	 *
	 * @param vertexId the id of the vertex
	 * @param count the number of vertices to retrieve
	 * @return the list of vertices if all found, otherwise an empty list
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
