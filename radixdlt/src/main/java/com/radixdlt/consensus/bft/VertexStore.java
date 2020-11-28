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

package com.radixdlt.consensus.bft;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSet.Builder;
import com.radixdlt.consensus.QuorumCertificate;
import com.radixdlt.consensus.HighQC;
import com.radixdlt.consensus.Ledger;
import com.radixdlt.consensus.BFTHeader;
import com.google.common.hash.HashCode;

import com.google.common.collect.ImmutableList;
import com.radixdlt.environment.EventDispatcher;

import com.radixdlt.utils.Pair;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import javax.annotation.concurrent.NotThreadSafe;

/**
 * Manages the BFT Vertex chain.
 * TODO: Move this logic into ledger package.
 */
@NotThreadSafe
public final class VertexStore {

	// TODO: combine all of the following senders as an update sender
	public interface VertexStoreEventSender {
		void highQC(QuorumCertificate qc);
	}
	private final VertexStoreEventSender vertexStoreEventSender;
	private final EventDispatcher<BFTUpdate> bftUpdateDispatcher;
	private final EventDispatcher<BFTCommittedUpdate> bftCommittedDispatcher;

	private final PersistentVertexStore persistentVertexStore;
	private final Ledger ledger;

	private final Map<HashCode, PreparedVertex> vertices = new HashMap<>();
	private final Map<HashCode, Set<HashCode>> vertexChildren = new HashMap<>();

	// These should never be null
	private VerifiedVertex rootVertex;
	private QuorumCertificate highestQC;
	private QuorumCertificate highestCommittedQC;

	private VertexStore(
		PersistentVertexStore persistentVertexStore,
		Ledger ledger,
		VerifiedVertex rootVertex,
		QuorumCertificate commitQC,
		QuorumCertificate highestQC,
		EventDispatcher<BFTUpdate> bftUpdateDispatcher,
		EventDispatcher<BFTCommittedUpdate> bftCommittedDispatcher,
		VertexStoreEventSender vertexStoreEventSender
	) {
		this.persistentVertexStore = Objects.requireNonNull(persistentVertexStore);
		this.ledger = Objects.requireNonNull(ledger);
		this.vertexStoreEventSender = Objects.requireNonNull(vertexStoreEventSender);
		this.bftUpdateDispatcher = Objects.requireNonNull(bftUpdateDispatcher);
		this.bftCommittedDispatcher = Objects.requireNonNull(bftCommittedDispatcher);
		this.rootVertex = Objects.requireNonNull(rootVertex);
		this.highestQC = Objects.requireNonNull(highestQC);
		this.highestCommittedQC = Objects.requireNonNull(commitQC);
		this.vertexChildren.put(rootVertex.getId(), new HashSet<>());
	}

	public static VertexStore create(
		PersistentVertexStore persistentVertexStore,
		VerifiedVertexStoreState vertexStoreState,
		Ledger ledger,
		EventDispatcher<BFTUpdate> bftUpdateDispatcher,
		EventDispatcher<BFTCommittedUpdate> bftCommittedDispatcher,
		VertexStoreEventSender vertexStoreEventSender
	) {
		VertexStore vertexStore = new VertexStore(
			persistentVertexStore,
			ledger,
			vertexStoreState.getRoot(),
			vertexStoreState.getHighQC().highestCommittedQC(),
			vertexStoreState.getHighQC().highestQC(),
			bftUpdateDispatcher,
			bftCommittedDispatcher,
			vertexStoreEventSender
		);

		for (VerifiedVertex vertex : vertexStoreState.getVertices()) {
			LinkedList<PreparedVertex> previous = vertexStore.getPathFromRoot(vertex.getParentId());
			Optional<PreparedVertex> preparedVertexMaybe = ledger.prepare(previous, vertex);
			if (preparedVertexMaybe.isEmpty()) {
				break;
			} else {
				PreparedVertex preparedVertex = preparedVertexMaybe.get();
				vertexStore.vertices.put(preparedVertex.getId(), preparedVertex);
				vertexStore.vertexChildren.put(preparedVertex.getId(), new HashSet<>());
				Set<HashCode> siblings = vertexStore.vertexChildren.get(preparedVertex.getParentId());
				siblings.add(preparedVertex.getId());
			}
		}

		return vertexStore;
	}

	public VerifiedVertex getRoot() {
		return rootVertex;
	}

	public boolean tryRebuild(VerifiedVertexStoreState vertexStoreState) {

		// FIXME: Currently this assumes vertexStoreState is a chain with no forks which is our only use case at the moment.
		LinkedList<PreparedVertex> prepared = new LinkedList<>();
		for (VerifiedVertex vertex : vertexStoreState.getVertices()) {
			Optional<PreparedVertex> preparedVertexMaybe = ledger.prepare(prepared, vertex);
			if (preparedVertexMaybe.isEmpty()) {
				return false;
			}

			prepared.add(preparedVertexMaybe.get());
		}

		this.rootVertex = vertexStoreState.getRoot();
		this.highestCommittedQC = vertexStoreState.getHighQC().highestCommittedQC();
		this.highestQC = vertexStoreState.getHighQC().highestQC();
		this.vertices.clear();
		this.vertexChildren.clear();
		this.vertexChildren.put(rootVertex.getId(), new HashSet<>());

		for (PreparedVertex preparedVertex : prepared) {
			this.vertices.put(preparedVertex.getId(), preparedVertex);
			this.vertexChildren.put(preparedVertex.getId(), new HashSet<>());
			Set<HashCode> siblings = vertexChildren.get(preparedVertex.getParentId());
			siblings.add(preparedVertex.getId());
		}

		// TODO: refactor this out
		this.persistentVertexStore.save(vertexStoreState);
		// TODO: combine all these bft updates into one
		this.vertexStoreEventSender.highQC(highestQC);
		bftUpdateDispatcher.dispatch(BFTUpdate.fromRebuild(vertexStoreState));
		return true;
	}

	public boolean containsVertex(HashCode vertexId) {
		return vertices.containsKey(vertexId) || rootVertex.getId().equals(vertexId);
	}

	public boolean addQC(QuorumCertificate qc) {
		if (!this.containsVertex(qc.getProposed().getVertexId())) {
			return false;
		}

		if (!vertexChildren.get(qc.getProposed().getVertexId()).isEmpty()) {
			// TODO: Check to see if qc's match in case there's a fault
			return true;
		}

		if (highestQC.getView().compareTo(qc.getView()) < 0) {
			highestQC = qc;
			vertexStoreEventSender.highQC(qc);
			// TODO: we lose all other tail QCs on this save, Not sure if this is okay...investigate...
			this.persistentVertexStore.save(getState());
		}

		qc.getCommittedAndLedgerStateProof().map(Pair::getFirst)
			.ifPresent(header -> this.commit(header, qc));

		return true;
	}

	private void getChildrenVerticesList(VerifiedVertex parent, ImmutableList.Builder<VerifiedVertex> builder) {
		Set<HashCode> childrenIds = this.vertexChildren.get(parent.getId());
		if (childrenIds == null) {
			return;
		}

		for (HashCode childId : childrenIds) {
			VerifiedVertex v = vertices.get(childId).getVertex();
			builder.add(v);
			getChildrenVerticesList(v, builder);
		}
	}

	private VerifiedVertexStoreState getState() {
		// TODO: store list dynamically rather than recomputing
		ImmutableList.Builder<VerifiedVertex> verticesBuilder = ImmutableList.builder();
		getChildrenVerticesList(this.rootVertex, verticesBuilder);
		return VerifiedVertexStoreState.create(
			this.highQC(),
			this.rootVertex,
			verticesBuilder.build()
		);
	}

	/**
	 * Inserts a vertex and then attempts to create the next header.
	 * If the ledger is ahead of the vertex store then returns an empty optional
	 * otherwise an empty optional.
	 *
	 * @param vertex vertex to insert
	 * @return a bft header if creation of next header is successful.
	 */
	public Optional<BFTHeader> insertVertex(VerifiedVertex vertex) {
		PreparedVertex v = vertices.get(vertex.getId());
		if (v != null) {
			return Optional.of(new BFTHeader(
				v.getVertex().getView(),
				v.getVertex().getId(),
				v.getLedgerHeader()
			));
		}

		if (!this.containsVertex(vertex.getParentId())) {
			throw new MissingParentException(vertex.getParentId());
		}

		return insertVertexInternal(vertex);
	}

	private Optional<BFTHeader> insertVertexInternal(VerifiedVertex vertex) {
		LinkedList<PreparedVertex> previous = getPathFromRoot(vertex.getParentId());
		Optional<PreparedVertex> preparedVertexMaybe = ledger.prepare(previous, vertex);
		preparedVertexMaybe.ifPresent(preparedVertex -> {
			vertices.put(preparedVertex.getId(), preparedVertex);
			vertexChildren.put(preparedVertex.getId(), new HashSet<>());
			Set<HashCode> siblings = vertexChildren.get(preparedVertex.getParentId());
			siblings.add(preparedVertex.getId());

			VerifiedVertexStoreState vertexStoreState = getState();
			bftUpdateDispatcher.dispatch(BFTUpdate.insertedVertex(vertex, siblings.size(), vertexStoreState));
			this.persistentVertexStore.save(vertexStoreState);
		});

		return preparedVertexMaybe
			.map(executedVertex -> new BFTHeader(vertex.getView(), vertex.getId(), executedVertex.getLedgerHeader()));
	}

	private void removeVertexAndPruneInternal(HashCode vertexId, HashCode skip, Builder<HashCode> prunedVerticesBuilder) {
		vertices.remove(vertexId);

		if (this.rootVertex.getId().equals(vertexId)) {
			return;
		}

		if (skip != null) {
			prunedVerticesBuilder.add(vertexId);
		}

		Set<HashCode> children = vertexChildren.remove(vertexId);
		for (HashCode child : children) {
			if (!child.equals(skip)) {
				removeVertexAndPruneInternal(child, null, prunedVerticesBuilder);
			}
		}
	}

	/**
	 * Commit a vertex. Executes the atom and prunes the tree.
	 * @param header the header to be committed
	 * @param commitQC the proof of commit
	 */
	private void commit(BFTHeader header, QuorumCertificate commitQC) {
		if (header.getView().compareTo(this.rootVertex.getView()) <= 0) {
			return;
		}

		final HashCode vertexId = header.getVertexId();
		final VerifiedVertex tipVertex = vertices.get(vertexId).getVertex();
		if (tipVertex == null) {
			throw new IllegalStateException("Committing vertex not in store: " + header);
		}

		this.rootVertex = tipVertex;
		this.highestCommittedQC = commitQC;
		Builder<HashCode> prunedSetBuilder = ImmutableSet.builder();
		final ImmutableList<PreparedVertex> path = ImmutableList.copyOf(getPathFromRoot(tipVertex.getId()));
		HashCode prev = null;
		for (int i = path.size() - 1; i >= 0; i--) {
			this.removeVertexAndPruneInternal(path.get(i).getId(), prev, prunedSetBuilder);
			prev = path.get(i).getId();
		}

		VerifiedVertexStoreState vertexStoreState = getState();
		ImmutableSet<HashCode> pruned = prunedSetBuilder.build();
		final BFTCommittedUpdate bftCommittedUpdate = new BFTCommittedUpdate(pruned, path, vertexStoreState);
		// TODO: Make these two persistent saves atomic
		this.bftCommittedDispatcher.dispatch(bftCommittedUpdate);
		this.ledger.commit(pruned, path, vertexStoreState);
		this.persistentVertexStore.save(vertexStoreState);
	}

	public LinkedList<PreparedVertex> getPathFromRoot(HashCode vertexId) {
		final LinkedList<PreparedVertex> path = new LinkedList<>();

		PreparedVertex vertex = vertices.get(vertexId);
		while (vertex != null) {
			path.addFirst(vertex);
			vertex = vertices.get(vertex.getParentId());
		}

		return path;
	}

	/**
	 * Retrieves the highest QC and highest committed QC in the store.
	 *
	 * @return the highest QCs
	 */
	public HighQC highQC() {
		return HighQC.from(this.highestQC, this.highestCommittedQC);
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
	public Optional<ImmutableList<VerifiedVertex>> getVertices(HashCode vertexId, int count) {
		HashCode nextId = vertexId;
		ImmutableList.Builder<VerifiedVertex> builder = ImmutableList.builderWithExpectedSize(count);
		for (int i = 0; i < count; i++) {
			final VerifiedVertex verifiedVertex;
			if (nextId.equals(rootVertex.getId())) {
				verifiedVertex = rootVertex;
			} else if (this.vertices.containsKey(nextId)) {
				final PreparedVertex preparedVertex = this.vertices.get(nextId);
				verifiedVertex = preparedVertex.getVertex();
			} else {
				return Optional.empty();
			}

			builder.add(verifiedVertex);
			nextId = verifiedVertex.getParentId();
		}

		return Optional.of(builder.build());
	}
}
