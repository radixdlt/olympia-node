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
import com.google.common.hash.HashCode;
import com.radixdlt.consensus.BFTHeader;
import com.radixdlt.consensus.QuorumCertificate;
import com.radixdlt.consensus.VerifiedLedgerHeaderAndProof;
import com.radixdlt.store.berkeley.SerializedVertexStoreState;
import com.radixdlt.utils.Pair;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

public final class VerifiedVertexStoreState {
	private final VerifiedVertex root;
	private final VerifiedLedgerHeaderAndProof rootHeader;
	private final QuorumCertificate rootCommitQC;
	private final ImmutableList<VerifiedVertex> vertices;

	private VerifiedVertexStoreState(
		QuorumCertificate rootCommitQC,
		VerifiedLedgerHeaderAndProof rootHeader,
		VerifiedVertex root,
		ImmutableList<VerifiedVertex> vertices
	) {
		this.rootCommitQC = rootCommitQC;
		this.rootHeader = rootHeader;
		this.root = root;
		this.vertices = vertices;
	}

	public static VerifiedVertexStoreState create(
		QuorumCertificate rootCommitQC,
		VerifiedVertex root
	) {
		return create(rootCommitQC, root, ImmutableList.of());
	}

	public static VerifiedVertexStoreState create(
		QuorumCertificate rootCommitQC,
		VerifiedVertex root,
		ImmutableList<VerifiedVertex> vertices
	) {
		final Pair<BFTHeader, VerifiedLedgerHeaderAndProof> headers = rootCommitQC.getCommittedAndLedgerStateProof().orElseThrow(
			() -> new IllegalStateException(String.format("rootCommit=%s does not have commit", rootCommitQC))
		);
		VerifiedLedgerHeaderAndProof rootHeader = headers.getSecond();
		BFTHeader bftHeader = headers.getFirst();
		if (!bftHeader.getVertexId().equals(root.getId())) {
			throw new IllegalStateException(String.format("rootCommitQC=%s does not match rootVertex=%s", rootCommitQC, root));
		}

		if (Stream.concat(Stream.of(root), vertices.stream())
			.map(VerifiedVertex::getId)
			.noneMatch(rootCommitQC.getProposed().getVertexId()::equals)) {
			throw new IllegalStateException(String.format("rootCommitQC=%s proposed does not have a corresponding vertex", rootCommitQC));
		}

		if (Stream.concat(Stream.of(root), vertices.stream())
			.map(VerifiedVertex::getId)
			.noneMatch(rootCommitQC.getParent().getVertexId()::equals)) {
			throw new IllegalStateException(String.format("rootCommitQC=%s parent does not have a corresponding vertex", rootCommitQC));
		}

		Set<HashCode> seen = new HashSet<>();
		seen.add(root.getId());
		for (VerifiedVertex v : vertices) {
			if (!seen.contains(v.getParentId())) {
				throw new IllegalStateException(String.format("Missing qc=%s {root=%s vertices=%s}", v.getQC(), root, vertices));
			}
			seen.add(v.getId());
		}

		return new VerifiedVertexStoreState(rootCommitQC, rootHeader, root, vertices);
	}

	public SerializedVertexStoreState toSerialized() {
		return new SerializedVertexStoreState(
			rootCommitQC,
			root.toSerializable(),
			vertices.stream().map(VerifiedVertex::toSerializable).collect(ImmutableList.toImmutableList())
		);
	}

	public QuorumCertificate getRootCommitQC() {
		return rootCommitQC;
	}

	public VerifiedVertex getRoot() {
		return root;
	}

	public ImmutableList<VerifiedVertex> getVertices() {
		return vertices;
	}

	public VerifiedLedgerHeaderAndProof getRootHeader() {
		return rootHeader;
	}

	@Override
	public int hashCode() {
		return Objects.hash(root, rootHeader, rootCommitQC, vertices);
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof VerifiedVertexStoreState)) {
			return false;
		}

		VerifiedVertexStoreState other = (VerifiedVertexStoreState) o;
		return Objects.equals(this.root, other.root)
			&& Objects.equals(this.rootHeader, other.rootHeader)
			&& Objects.equals(this.rootCommitQC, other.rootCommitQC)
			&& Objects.equals(this.vertices, other.vertices);
	}
}
