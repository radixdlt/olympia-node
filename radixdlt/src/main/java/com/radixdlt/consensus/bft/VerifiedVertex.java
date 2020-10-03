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

import com.radixdlt.consensus.BFTHeader;
import com.radixdlt.consensus.Command;
import com.radixdlt.consensus.LedgerHeader;
import com.radixdlt.consensus.QuorumCertificate;
import com.radixdlt.consensus.UnverifiedVertex;
import com.radixdlt.consensus.bft.PreparedVertex.CommandStatus;
import com.radixdlt.crypto.Hash;
import java.util.Objects;

/**
 * A vertex which has been verified with hash id
 */
public final class VerifiedVertex {
	private final UnverifiedVertex vertex;
	private final Hash id;

	public VerifiedVertex(UnverifiedVertex vertex, Hash id) {
		this.vertex = Objects.requireNonNull(vertex);
		this.id = Objects.requireNonNull(id);
	}

	public UnverifiedVertex toSerializable() {
		return vertex;
	}

	public Command getCommand() {
		return vertex.getCommand();
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

	public Hash getId() {
		return id;
	}

	public Hash getParentId() {
		return vertex.getQC().getProposed().getVertexId();
	}


	public interface ExecutedVertexBuilder {
		PreparedVertex andCommandStatus(CommandStatus commandStatus);
	}

	public ExecutedVertexBuilder withHeader(LedgerHeader ledgerHeader) {
		return status -> new PreparedVertex(this, ledgerHeader, status);
	}

	@Override
	public String toString() {
		return String.format("%s{view=%s id=%s}", this.getClass().getSimpleName(), this.vertex.getView(), this.id);
	}
}
