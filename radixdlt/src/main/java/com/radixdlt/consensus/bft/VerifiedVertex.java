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
import com.radixdlt.consensus.QuorumCertificate;
import com.radixdlt.consensus.Vertex;
import com.radixdlt.crypto.Hash;
import java.util.Objects;

/**
 * A vertex which has been verified with hash id
 */
public final class VerifiedVertex {
	private final Vertex vertex;
	private final Hash id;

	public VerifiedVertex(Vertex vertex, Hash id) {
		this.vertex = Objects.requireNonNull(vertex);
		this.id = Objects.requireNonNull(id);
	}

	public Vertex toRaw() {
		return vertex;
	}

	public Command getCommand() {
		return vertex.getCommand();
	}

	public boolean touchesGenesis() {
		return vertex.getView().isGenesis()
			|| vertex.getParentHeader().getView().isGenesis()
			|| vertex.getGrandParentHeader().getView().isGenesis();
	}

	public boolean hasDirectParent() {
		return vertex.hasDirectParent();
	}

	public boolean parentHasDirectParent() {
		return vertex.getParentHeader().getView().equals(vertex.getGrandParentHeader().getView().next());
	}

	public BFTHeader getParentHeader() {
		return vertex.getParentHeader();
	}

	public BFTHeader getGrandParentHeader() {
		return vertex.getGrandParentHeader();
	}

	public View getView() {
		return vertex.getView();
	}

	public QuorumCertificate getQC() {
		return vertex.getQC();
	}

	public Vertex getVertex() {
		return vertex;
	}

	public Hash getId() {
		return id;
	}

	public Hash getParentId() {
		return vertex.getQC().getProposed().getVertexId();
	}

}
