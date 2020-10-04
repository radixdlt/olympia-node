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

import com.radixdlt.consensus.Command;
import com.radixdlt.consensus.VerifiedLedgerHeaderAndProof;
import com.radixdlt.consensus.QuorumCertificate;
import com.radixdlt.consensus.Ledger;
import com.radixdlt.consensus.BFTHeader;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.counters.SystemCounters.CounterType;
import com.radixdlt.crypto.Hash;

import com.google.common.collect.ImmutableList;
import com.radixdlt.ledger.VerifiedCommandsAndProof;
import com.radixdlt.utils.Pair;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import javax.annotation.concurrent.NotThreadSafe;

/**
 * Manages the BFT Vertex chain.
 * TODO: Move this logic into ledger package.
 */
@NotThreadSafe
public final class VertexStore {
	// TODO: combine all of the following senders as an update sender
	public interface BFTUpdateSender {
		void sendBFTUpdate(BFTUpdate bftUpdate);
	}

	public interface VertexStoreEventSender {
		void sendCommitted(BFTCommittedUpdate committedUpdate);
		void highQC(QuorumCertificate qc);
	}

	private final VertexStoreEventSender vertexStoreEventSender;
	private final BFTUpdateSender bftUpdateSender;
	private final Ledger ledger;
	private final SystemCounters counters;
	private final Map<Hash, PreparedVertex> vertices = new HashMap<>();
	private final Map<Hash, Integer> vertexNumChildren = new HashMap<>();

	// These should never be null
	private VerifiedVertex rootVertex;
	private QuorumCertificate highestQC;
	private QuorumCertificate highestCommittedQC;

	public VertexStore(
		VerifiedVertex rootVertex,
		QuorumCertificate rootQC,
		Ledger ledger,
		BFTUpdateSender bftUpdateSender,
		VertexStoreEventSender vertexStoreEventSender,
		SystemCounters counters
	) {
		this(
			rootVertex,
			rootQC,
			Collections.emptyList(),
			ledger,
			bftUpdateSender,
			vertexStoreEventSender,
			counters
		);
	}

	public VertexStore(
		VerifiedVertex rootVertex,
		QuorumCertificate rootQC,
		List<VerifiedVertex> vertices,
		Ledger ledger,
		BFTUpdateSender bftUpdateSender,
		VertexStoreEventSender vertexStoreEventSender,
		SystemCounters counters
	) {
		this.ledger = Objects.requireNonNull(ledger);
		this.vertexStoreEventSender = Objects.requireNonNull(vertexStoreEventSender);
		this.bftUpdateSender = Objects.requireNonNull(bftUpdateSender);
		this.counters = Objects.requireNonNull(counters);

		Objects.requireNonNull(rootVertex);
		Objects.requireNonNull(rootQC);
		Objects.requireNonNull(vertices);

		this.rebuild(rootVertex, rootQC, rootQC, vertices);
	}

	public VerifiedVertex getRoot() {
		return rootVertex;
	}

	public void rebuild(VerifiedVertex rootVertex, QuorumCertificate rootQC, QuorumCertificate rootCommitQC, List<VerifiedVertex> vertices) {
		if (!rootQC.getProposed().getVertexId().equals(rootVertex.getId())) {
			throw new IllegalStateException(String.format("rootQC=%s does not match rootVertex=%s", rootQC, rootVertex));
		}

		final Optional<BFTHeader> header = rootCommitQC.getCommittedAndLedgerStateProof().map(Pair::getFirst);
		if (!header.isPresent()) {
			if (!rootQC.getView().isGenesis() || !rootQC.equals(rootCommitQC)) {
				throw new IllegalStateException(String.format("rootCommit=%s does not have commit", rootCommitQC));
			}
		} else if (!header.get().getVertexId().equals(rootVertex.getId())) {
			throw new IllegalStateException(String.format("rootCommitQC=%s does not match rootVertex=%s", rootCommitQC, rootVertex));
		}

		this.vertices.clear();
		this.vertexNumChildren.clear();
		this.rootVertex = rootVertex;
		this.highestQC = rootQC;
		this.vertexStoreEventSender.highQC(rootQC);
		this.highestCommittedQC = rootCommitQC;

		for (VerifiedVertex vertex : vertices) {
			if (!addQC(vertex.getQC())) {
				throw new IllegalStateException(String.format("Missing qc=%s", vertex.getQC()));
			}

			insertVertex(vertex);
		}
	}

	public boolean containsVertex(Hash vertexId) {
		return vertices.containsKey(vertexId) || rootVertex.getId().equals(vertexId);
	}

	public boolean addQC(QuorumCertificate qc) {
		if (!this.containsVertex(qc.getProposed().getVertexId())) {
			return false;
		}

		// TODO: check if already added

		if (highestQC.getView().compareTo(qc.getView()) < 0) {
			highestQC = qc;
			vertexStoreEventSender.highQC(qc);
		}

		qc.getCommittedAndLedgerStateProof().ifPresent(headerAndProof -> {
			BFTHeader highest = this.highestCommittedQC.getCommittedAndLedgerStateProof()
				.map(Pair::getFirst)
				.orElseThrow(() ->
					new IllegalStateException(String.format("Highest Committed does not have a commit: %s", this.highestCommittedQC))
				);

			BFTHeader header = headerAndProof.getFirst();
			if (highest.getView().compareTo(header.getView()) < 0) {
				this.highestCommittedQC = qc;
			}

			VerifiedLedgerHeaderAndProof proof = headerAndProof.getSecond();
			this.commit(header, proof);
		});

		return true;
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
		if (!this.containsVertex(vertex.getParentId())) {
			throw new MissingParentException(vertex.getParentId());
		}

		LinkedList<PreparedVertex> previous = getPathFromRoot(vertex.getParentId());
		Optional<PreparedVertex> preparedVertexMaybe = ledger.prepare(previous, vertex);
		preparedVertexMaybe.ifPresent(preparedVertex -> {
			// TODO: Don't check for state computer errors for now so that we don't
			// TODO: have to deal with failing leader proposals
			// TODO: Reinstate this when ProposalGenerator + Mempool can guarantee correct proposals
			// TODO: (also see commitVertex->storeAtom)
			if (!vertices.containsKey(preparedVertex.getId())) {
				vertices.put(preparedVertex.getId(), preparedVertex);
				int numChildren = vertexNumChildren.merge(preparedVertex.getParentId(), 1, Integer::sum);
				if (numChildren > 1) {
					this.counters.increment(CounterType.BFT_VERTEX_STORE_FORKS);
				}
				if (!vertex.hasDirectParent()) {
					this.counters.increment(CounterType.BFT_INDIRECT_PARENT);
				}

				updateVertexStoreSize();

				final BFTUpdate update = new BFTUpdate(vertex);
				bftUpdateSender.sendBFTUpdate(update);
			}
		});

		return preparedVertexMaybe
			.map(executedVertex -> new BFTHeader(vertex.getView(), vertex.getId(), executedVertex.getLedgerHeader()));
	}

	/**
	 * Commit a vertex. Executes the atom and prunes the tree. Returns
	 * the Vertex if commit was successful. If the store is ahead of
	 * what is to be committed, returns an empty optional
	 *
	 * @param header the proof of commit
	 */
	private void commit(BFTHeader header, VerifiedLedgerHeaderAndProof proof) {
		if (header.getView().compareTo(this.rootVertex.getView()) <= 0) {
			return;
		}

		final Hash vertexId = header.getVertexId();
		final VerifiedVertex tipVertex = vertices.get(vertexId).getVertex();
		if (tipVertex == null) {
			throw new IllegalStateException("Committing vertex not in store: " + header);
		}

		final ImmutableList<PreparedVertex> path = ImmutableList.copyOf(getPathFromRoot(tipVertex.getId()));

		// TODO: Must prune all other children of root
		path.forEach(v -> {
			vertices.remove(v.getId());
			vertexNumChildren.remove(v.getParentId());
		});

		final ImmutableList<Command> commands = path.stream()
			.flatMap(PreparedVertex::getCommands)
			.collect(ImmutableList.toImmutableList());

		this.counters.add(CounterType.BFT_PROCESSED, path.size());
		final BFTCommittedUpdate bftCommittedUpdate = new BFTCommittedUpdate(path, proof);
		this.vertexStoreEventSender.sendCommitted(bftCommittedUpdate);

		VerifiedCommandsAndProof verifiedCommandsAndProof = new VerifiedCommandsAndProof(commands, proof);
		this.ledger.commit(verifiedCommandsAndProof);

		rootVertex = tipVertex;

		updateVertexStoreSize();
	}

	public LinkedList<PreparedVertex> getPathFromRoot(Hash vertexId) {
		final LinkedList<PreparedVertex> path = new LinkedList<>();

		PreparedVertex vertex = vertices.get(vertexId);
		while (vertex != null) {
			path.addFirst(vertex);
			vertex = vertices.get(vertex.getParentId());
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
	public Optional<ImmutableList<VerifiedVertex>> getVertices(Hash vertexId, int count) {
		Hash nextId = vertexId;
		ImmutableList.Builder<VerifiedVertex> builder = ImmutableList.builderWithExpectedSize(count);
		for (int i = 0; i < count; i++) {
			final PreparedVertex preparedVertex = this.vertices.get(nextId);
			final VerifiedVertex verifiedVertex;
			if (preparedVertex != null) {
				verifiedVertex = preparedVertex.getVertex();
			} else if (nextId.equals(rootVertex.getId())) {
				verifiedVertex = rootVertex;
			} else {
				return Optional.empty();
			}

			builder.add(verifiedVertex);
			nextId = verifiedVertex.getParentId();
		}

		return Optional.of(builder.build());
	}

	public int getSize() {
		return vertices.size();
	}

	private void updateVertexStoreSize() {
		this.counters.set(CounterType.BFT_VERTEX_STORE_SIZE, this.vertices.size());
	}
}
