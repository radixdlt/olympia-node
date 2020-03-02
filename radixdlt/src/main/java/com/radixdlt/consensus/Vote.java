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

import com.fasterxml.jackson.annotation.JsonProperty;
import com.radixdlt.common.EUID;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.SerializerConstants;
import com.radixdlt.serialization.SerializerDummy;
import com.radixdlt.serialization.SerializerId2;
import com.radixdlt.serialization.DsonOutput.Output;

import java.util.Objects;

/**
 * Represents a vote on a vertex
 */
@SerializerId2("consensus.vote")
public final class Vote {
	@JsonProperty(SerializerConstants.SERIALIZER_NAME)
	@DsonOutput(value = {Output.API, Output.WIRE, Output.PERSIST})
	SerializerDummy serializer = SerializerDummy.DUMMY;

	@JsonProperty("author")
	@DsonOutput(Output.ALL)
	private final EUID author;

	@JsonProperty("vertex_metadata")
	@DsonOutput(Output.ALL)
	private final VertexMetadata vertexMetadata;

	// TODO add signature

	Vote() {
		// Serializer only
		this.author = null;
		this.vertexMetadata = null;
	}

	public Vote(EUID author, VertexMetadata vertexMetadata) {
		this.author = Objects.requireNonNull(author);
		this.vertexMetadata = Objects.requireNonNull(vertexMetadata);
	}

	public EUID getAuthor() {
		return author;
	}

	public VertexMetadata getVertexMetadata() {
		return vertexMetadata;
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.author, this.vertexMetadata);
	}

	@Override
	public boolean equals(Object o) {
		if (o == this) {
			return true;
		}
		if (o instanceof Vote) {
			Vote other = (Vote) o;
			return
				Objects.equals(this.author, other.author) &&
				Objects.equals(this.vertexMetadata, other.vertexMetadata);
		}
		return false;
	}
}
