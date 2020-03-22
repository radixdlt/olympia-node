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
import com.google.errorprone.annotations.Immutable;
import com.radixdlt.crypto.CryptoException;
import com.radixdlt.crypto.ECDSASignature;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.SerializerConstants;
import com.radixdlt.serialization.SerializerDummy;
import com.radixdlt.serialization.SerializerId2;

import java.util.Objects;
import java.util.Optional;

/**
 * Represents a vote on a vertex
 */
@SerializerId2("consensus.vote")
@Immutable // author cannot be but is effectively final because of serializer
public final class Vote {
	@JsonProperty(SerializerConstants.SERIALIZER_NAME)
	@DsonOutput(value = {Output.API, Output.WIRE, Output.PERSIST})
	SerializerDummy serializer = SerializerDummy.DUMMY;

	private ECPublicKey author;

	@JsonProperty("vertex_metadata")
	@DsonOutput(Output.ALL)
	private final VertexMetadata vertexMetadata;

	@JsonProperty("signature")
	@DsonOutput(Output.ALL)
	private final ECDSASignature signature; // may be null if not signed (e.g. for genesis)

	Vote() {
		// Serializer only
		this.author = null;
		this.vertexMetadata = null;
		this.signature = null;
	}

	public Vote(ECPublicKey author, VertexMetadata vertexMetadata, ECDSASignature signature) {
		this.author = Objects.requireNonNull(author);
		this.vertexMetadata = Objects.requireNonNull(vertexMetadata);
		this.signature = signature;
	}

	public ECPublicKey getAuthor() {
		return author;
	}

	public VertexMetadata getVertexMetadata() {
		return vertexMetadata;
	}

	public Optional<ECDSASignature> getSignature() {
		return Optional.ofNullable(this.signature);
	}

	@JsonProperty("author")
	@DsonOutput(Output.ALL)
	private byte[] getSerializerAuthor() {
		return this.author == null ? null : this.author.getBytes();
	}
	@JsonProperty("author")
	private void setSerializerAuthor(byte[] author) throws CryptoException {
		this.author = (author == null) ? null : new ECPublicKey(author);
	}

	@Override
	public String toString() {
		return String.format("Vote{author=%s view=%s}", author.getUID().toString().substring(0, 6), vertexMetadata.getView());
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.author, this.vertexMetadata, this.signature);
	}

	@Override
	public boolean equals(Object o) {
		if (o == this) {
			return true;
		}
		if (o instanceof Vote) {
			Vote other = (Vote) o;
			return
				Objects.equals(this.author, other.author)
					&& Objects.equals(this.vertexMetadata, other.vertexMetadata)
					&& Objects.equals(this.signature, other.signature);
		}
		return false;
	}
}
