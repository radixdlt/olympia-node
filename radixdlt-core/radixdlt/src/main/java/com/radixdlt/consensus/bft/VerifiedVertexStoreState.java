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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.hash.HashCode;
import com.radixdlt.consensus.BFTHeader;
import com.radixdlt.consensus.HighQC;
import com.radixdlt.consensus.TimeoutCertificate;
import com.radixdlt.consensus.LedgerProof;
import com.radixdlt.crypto.Hasher;
import com.radixdlt.store.berkeley.SerializedVertexStoreState;
import com.radixdlt.utils.Pair;
import java.util.HashMap;
import java.util.Objects;
import java.util.Optional;
import javax.annotation.concurrent.Immutable;

/**
 * State of the vertex store which can be serialized.
 */
@Immutable
public final class VerifiedVertexStoreState {
	private final VerifiedVertex root;
	private final LedgerProof rootHeader;
	private final HighQC highQC;
	// TODO: collapse the following two
	private final ImmutableList<VerifiedVertex> vertices;
	private final ImmutableMap<HashCode, VerifiedVertex> idToVertex;
	private Optional<TimeoutCertificate> highestTC;

	private VerifiedVertexStoreState(
		HighQC highQC,
		LedgerProof rootHeader,
		VerifiedVertex root,
		ImmutableMap<HashCode, VerifiedVertex> idToVertex,
		ImmutableList<VerifiedVertex> vertices,
		Optional<TimeoutCertificate> highestTC
	) {
		this.highQC = highQC;
		this.rootHeader = rootHeader;
		this.root = root;
		this.idToVertex = idToVertex;
		this.vertices = vertices;
		this.highestTC = highestTC;
	}

	public static VerifiedVertexStoreState create(
		HighQC highQC,
		VerifiedVertex root,
		Optional<TimeoutCertificate> highestTC,
		Hasher hasher
	) {
		return create(highQC, root, ImmutableList.of(), highestTC, hasher);
	}

	public static VerifiedVertexStoreState create(
		HighQC highQC,
		VerifiedVertex root,
		ImmutableList<VerifiedVertex> vertices,
		Optional<TimeoutCertificate> highestTC,
		Hasher hasher
	) {
		final Pair<BFTHeader, LedgerProof> headers = highQC.highestCommittedQC()
			.getCommittedAndLedgerStateProof(hasher)
			.orElseThrow(() -> new IllegalStateException(String.format("highQC=%s does not have commit", highQC)));
		LedgerProof rootHeader = headers.getSecond();
		BFTHeader bftHeader = headers.getFirst();
		if (!bftHeader.getVertexId().equals(root.getId())) {
			throw new IllegalStateException(String.format("committedHeader=%s does not match rootVertex=%s", bftHeader, root));
		}

		HashMap<HashCode, VerifiedVertex> seen = new HashMap<>();
		seen.put(root.getId(), root);
		for (VerifiedVertex v : vertices) {
			if (!seen.containsKey(v.getParentId())) {
				throw new IllegalStateException(String.format("Missing qc=%s {root=%s vertices=%s}", v.getQC(), root, vertices));
			}
			seen.put(v.getId(), v);
		}
		ImmutableMap<HashCode, VerifiedVertex> idToVertex = ImmutableMap.copyOf(seen);

		if (seen.keySet().stream().noneMatch(highQC.highestCommittedQC().getProposed().getVertexId()::equals)) {
			throw new IllegalStateException(
				String.format("highQC=%s highCommitted proposed missing {root=%s vertices=%s}", highQC, root, vertices)
			);
		}

		if (seen.keySet().stream().noneMatch(highQC.highestCommittedQC().getParent().getVertexId()::equals)) {
			throw new IllegalStateException(
				String.format("highQC=%s highCommitted parent does not have a corresponding vertex", highQC)
			);
		}

		if (seen.keySet().stream().noneMatch(highQC.highestQC().getParent().getVertexId()::equals)) {
			throw new IllegalStateException(
				String.format("highQC=%s highQC parent does not have a corresponding vertex", highQC)
			);
		}

		if (seen.keySet().stream().noneMatch(highQC.highestQC().getProposed().getVertexId()::equals)) {
			throw new IllegalStateException(
				String.format("highQC=%s highQC proposed does not have a corresponding vertex", highQC)
			);
		}

		return new VerifiedVertexStoreState(highQC, rootHeader, root, idToVertex, vertices, highestTC);
	}

	public VerifiedVertexStoreState prune(Hasher hasher) {
		if (highQC.highestQC().getCommittedAndLedgerStateProof(hasher).isPresent()) {
			Pair<BFTHeader, LedgerProof> newHeaders = highQC.highestQC().getCommittedAndLedgerStateProof(hasher).get();
			BFTHeader header = newHeaders.getFirst();
			if (header.getView().gt(root.getView())) {
				VerifiedVertex newRoot = idToVertex.get(header.getVertexId());
				ImmutableList<VerifiedVertex> newVertices = ImmutableList.of(
					idToVertex.get(highQC.highestQC().getParent().getVertexId()),
					idToVertex.get(highQC.highestQC().getProposed().getVertexId())
				);
				ImmutableMap<HashCode, VerifiedVertex> idToVertex = ImmutableMap.of(
					highQC.highestQC().getParent().getVertexId(), newVertices.get(0),
					highQC.highestQC().getProposed().getVertexId(), newVertices.get(1)
				);
				HighQC newHighQC = HighQC.from(highQC.highestQC());
				LedgerProof proof = newHeaders.getSecond();
				return new VerifiedVertexStoreState(newHighQC, proof, newRoot, idToVertex, newVertices, highestTC);
			}
		}

		return this;
	}

	public SerializedVertexStoreState toSerialized() {
		return new SerializedVertexStoreState(
			this.highQC,
			this.root.toSerializable(),
			this.vertices.stream().map(VerifiedVertex::toSerializable).collect(ImmutableList.toImmutableList()),
			this.highestTC.orElse(null)
		);
	}

	public HighQC getHighQC() {
		return highQC;
	}

	public VerifiedVertex getRoot() {
		return root;
	}

	public ImmutableList<VerifiedVertex> getVertices() {
		return vertices;
	}

	public LedgerProof getRootHeader() {
		return rootHeader;
	}

	@Override
	public int hashCode() {
		return Objects.hash(root, rootHeader, highQC, idToVertex, vertices, highestTC);
	}

	@Override
	public boolean equals(Object o) {
		if (o == this) {
			return true;
		}

		if (!(o instanceof VerifiedVertexStoreState)) {
			return false;
		}

		VerifiedVertexStoreState other = (VerifiedVertexStoreState) o;
		return Objects.equals(this.root, other.root)
			&& Objects.equals(this.rootHeader, other.rootHeader)
			&& Objects.equals(this.highQC, other.highQC)
			&& Objects.equals(this.vertices, other.vertices)
			&& Objects.equals(this.idToVertex, other.idToVertex)
			&& Objects.equals(this.highestTC, other.highestTC);
	}
}
