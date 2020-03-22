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

@SerializerId2("consensus.qc")
public final class QuorumCertificate {
	@JsonProperty(SerializerConstants.SERIALIZER_NAME)
	@DsonOutput(value = {Output.API, Output.WIRE, Output.PERSIST})
	SerializerDummy serializer = SerializerDummy.DUMMY;

	@JsonProperty("signatures")
	@DsonOutput(Output.ALL)
	private final ECDSASignatures signatures;

	@JsonProperty("vertex_metadata")
	@DsonOutput(Output.ALL)
	private final VertexMetadata vertexMetadata;

	QuorumCertificate() {
		// Serializer only
		this.vertexMetadata = null;
		this.signatures = null;
	}

	public QuorumCertificate(VertexMetadata vertexMetadata, ECDSASignatures signatures) {
		this.vertexMetadata = Objects.requireNonNull(vertexMetadata);
		this.signatures = Objects.requireNonNull(signatures);
	}

	public View getView() {
		return vertexMetadata.getView();
	}

	public VertexMetadata getVertexMetadata() {
		return vertexMetadata;
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
			&& Objects.equals(vertexMetadata, that.vertexMetadata);
	}

	@Override
	public int hashCode() {
		return Objects.hash(signatures, vertexMetadata);
	}

	public String toString() {
		return String.format("QC{view=%s}", vertexMetadata.getView());
	}
}
