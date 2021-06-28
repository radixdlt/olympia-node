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

import com.radixdlt.atom.Txn;
import com.radixdlt.consensus.BFTHeader;
import com.radixdlt.consensus.LedgerHeader;
import com.radixdlt.consensus.QuorumCertificate;
import com.radixdlt.consensus.UnverifiedVertex;
import com.google.common.hash.HashCode;
import com.radixdlt.ledger.StateComputerLedger.PreparedTxn;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A vertex which has been verified with hash id
 */
public final class VerifiedVertex {
	private final UnverifiedVertex vertex;
	private final HashCode id;

	public VerifiedVertex(UnverifiedVertex vertex, HashCode id) {
		this.vertex = Objects.requireNonNull(vertex);
		this.id = Objects.requireNonNull(id);
	}

	public BFTNode getProposer() {
		return vertex.getProposer();
	}

	public boolean isTimeout() {
		return vertex.isTimeout();
	}

	public UnverifiedVertex toSerializable() {
		return vertex;
	}

	public List<Txn> getTxns() {
		return vertex.getTxns();
	}

	public boolean touchesGenesis() {
		return this.getView().isGenesis()
			|| this.getParentHeader().getView().isGenesis()
			|| this.getGrandParentHeader().getView().isGenesis();
	}

	public boolean hasDirectParent() {
		return this.vertex.getView().equals(this.getParentHeader().getView().next());
	}

	public boolean parentHasDirectParent() {
		return this.getParentHeader().getView().equals(this.getGrandParentHeader().getView().next());
	}

	public BFTHeader getParentHeader() {
		return vertex.getQC().getProposed();
	}

	public BFTHeader getGrandParentHeader() {
		return vertex.getQC().getParent();
	}

	public View getView() {
		return vertex.getView();
	}

	public QuorumCertificate getQC() {
		return vertex.getQC();
	}

	public HashCode getId() {
		return id;
	}

	public HashCode getParentId() {
		return vertex.getQC().getProposed().getVertexId();
	}


	public interface PreparedVertexBuilder {
		PreparedVertex andTxns(
			List<PreparedTxn> preparedTxns,
			Map<Txn, Exception> txnExceptions
		);
	}

	public PreparedVertexBuilder withHeader(LedgerHeader ledgerHeader, long timeOfExecution) {
		return (success, exceptions) -> new PreparedVertex(this, ledgerHeader, success, exceptions, timeOfExecution);
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.vertex, this.id);
	}

	@Override
	public boolean equals(Object o) {
		if (o instanceof VerifiedVertex) {
			final var that = (VerifiedVertex) o;
			return Objects.equals(this.id, that.id) && Objects.equals(this.vertex, that.vertex);
		}
		return false;
	}

	@Override
	public String toString() {
		return String.format("%s{epoch=%s view=%s parentView=%s id=%s}",
			this.getClass().getSimpleName(),
			this.vertex.getQC().getProposed().getLedgerHeader().getEpoch(),
			this.vertex.getView(),
			this.vertex.getQC().getProposed().getView(),
			this.id
		);
	}
}
