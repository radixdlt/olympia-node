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

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.radixdlt.crypto.ECDSASignatures;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.SerializerConstants;
import com.radixdlt.serialization.SerializerDummy;
import com.radixdlt.serialization.SerializerId2;
import com.radixdlt.serialization.DsonOutput.Output;
import java.util.Optional;

@SerializerId2("consensus.qc")
public final class QuorumCertificate {
	@JsonProperty(SerializerConstants.SERIALIZER_NAME)
	@DsonOutput(value = {Output.API, Output.WIRE, Output.PERSIST})
	SerializerDummy serializer = SerializerDummy.DUMMY;

	@JsonProperty("signatures")
	@DsonOutput(Output.ALL)
	private final ECDSASignatures signatures;

	@JsonProperty("vote_data")
	@DsonOutput(Output.ALL)
	private final VoteData voteData;

	QuorumCertificate() {
		// Serializer only
		this.voteData = null;
		this.signatures = null;
	}

	public QuorumCertificate(VoteData voteData, ECDSASignatures signatures) {
		this.voteData = Objects.requireNonNull(voteData);
		this.signatures = Objects.requireNonNull(signatures);
	}

	/**
	 * Create a mocked QC for genesis vertex
	 * @param genesisVertex the vertex to create a qc for
	 * @return a mocked QC
	 */
	public static QuorumCertificate ofGenesis(Vertex genesisVertex) {
		if (!genesisVertex.getView().isGenesis()) {
			throw new IllegalArgumentException(String.format("Vertex is not genesis: %s", genesisVertex));
		}

		VertexMetadata vertexMetadata = VertexMetadata.ofVertex(genesisVertex, false);
		final VoteData voteData = new VoteData(vertexMetadata, vertexMetadata, vertexMetadata);
		return new QuorumCertificate(voteData, new ECDSASignatures());
	}

	public View getView() {
		return voteData.getProposed().getView();
	}

	public VertexMetadata getProposed() {
		return voteData.getProposed();
	}

	public VertexMetadata getParent() {
		return voteData.getParent();
	}

	public Optional<VertexMetadata> getCommitted() {
		return voteData.getCommitted();
	}

	public VoteData getVoteData() {
		return voteData;
	}

	public ECDSASignatures getSignatures() {
		return signatures;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		QuorumCertificate that = (QuorumCertificate) o;
		return Objects.equals(signatures, that.signatures)
			&& Objects.equals(voteData, that.voteData);
	}

	@Override
	public int hashCode() {
		return Objects.hash(signatures, voteData);
	}

	@Override
	public String toString() {
		return String.format("QC{view=%s}", this.getView());
	}
}
