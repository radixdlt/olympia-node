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

package com.radixdlt.consensus;

import com.google.common.collect.ImmutableList;
import com.radixdlt.consensus.bft.BFTValidatorSet;
import com.radixdlt.consensus.bft.VerifiedVertex;
import com.radixdlt.utils.Pair;
import java.util.Objects;

/**
 * Configuration for a bft instance which should be shared by all validators in the bft.
 */
public final class BFTConfiguration {
	private final BFTValidatorSet validatorSet;
	private final QuorumCertificate qc;
	private final VerifiedLedgerHeaderAndProof rootHeader;
	private final VerifiedVertex rootVertex;
	private final ImmutableList<VerifiedVertex> vertices;

	public BFTConfiguration(
		BFTValidatorSet validatorSet,
		VerifiedVertex rootVertex,
		QuorumCertificate qc
	) {
		this(validatorSet, rootVertex, ImmutableList.of(), qc);
	}

	public BFTConfiguration(
		BFTValidatorSet validatorSet,
		VerifiedVertex rootVertex,
		ImmutableList<VerifiedVertex> vertices,
		QuorumCertificate qc
	) {
		this.validatorSet = Objects.requireNonNull(validatorSet);
		this.rootVertex = Objects.requireNonNull(rootVertex);
		this.vertices = Objects.requireNonNull(vertices);
		this.qc = Objects.requireNonNull(qc);
		this.rootHeader = qc.getCommittedAndLedgerStateProof().map(Pair::getSecond)
			.orElseThrow(() -> new IllegalArgumentException("genesisQC must be committed."));
	}

	public BFTValidatorSet getValidatorSet() {
		return validatorSet;
	}

	public VerifiedVertex getRootVertex() {
		return rootVertex;
	}

	public ImmutableList<VerifiedVertex> getVertices() {
		return vertices;
	}

	public VerifiedLedgerHeaderAndProof getRootHeader() {
		return this.rootHeader;
	}

	public QuorumCertificate getQC() {
		return qc;
	}

	@Override
	public int hashCode() {
		return Objects.hash(
			this.validatorSet,
			this.qc,
			this.rootHeader,
			this.rootVertex,
			this.vertices
		);
	}

	@Override
	public boolean equals(Object obj) {
		if (obj instanceof BFTConfiguration) {
			BFTConfiguration that = (BFTConfiguration) obj;
			return Objects.equals(this.validatorSet, that.validatorSet)
				&& Objects.equals(this.qc, that.qc)
				&& Objects.equals(this.rootHeader, that.rootHeader)
				&& Objects.equals(this.rootVertex, that.rootVertex)
				&& Objects.equals(this.vertices, that.vertices);
		}
		return false;
	}

	@Override
	public String toString() {
		return String.format("%s[validatorSet=%s, rootVertex=%s, qc=%s, header=%s]",
			getClass().getSimpleName(), this.validatorSet, this.rootVertex, this.qc, this.rootHeader);
	}
}
