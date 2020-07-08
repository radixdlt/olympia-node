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

package com.radixdlt.middleware2.network;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.radixdlt.consensus.VertexMetadata;
import com.radixdlt.crypto.CryptoException;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.SerializerId2;
import java.util.Objects;
import org.radix.network.messaging.Message;

@SerializerId2("message.consensus.get_epoch_response")
public class GetEpochResponseMessage extends Message {
	@JsonProperty("ancestor")
	@DsonOutput(Output.ALL)
	private final VertexMetadata ancestor;

	private ECPublicKey author;

	GetEpochResponseMessage() {
		// Serializer only
		super(0);
		this.author = null;
		this.ancestor = null;
	}

	GetEpochResponseMessage(ECPublicKey author, int magic, VertexMetadata ancestor) {
		super(magic);
		this.author = Objects.requireNonNull(author);
		this.ancestor = ancestor;
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

	public ECPublicKey getAuthor() {
		return author;
	}

	public VertexMetadata getAncestor() {
		return ancestor;
	}

	@Override
	public String toString() {
		return String.format("%s{ancestor=%s}", getClass().getSimpleName(), ancestor);
	}
}
