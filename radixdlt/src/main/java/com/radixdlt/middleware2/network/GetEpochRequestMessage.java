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
import com.radixdlt.crypto.CryptoException;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.SerializerId2;
import org.radix.network.messaging.Message;

@SerializerId2("message.consensus.get_epoch_request")
public class GetEpochRequestMessage extends Message {
	private ECPublicKey author;

	@JsonProperty("epoch")
	@DsonOutput(Output.ALL)
	private final long epoch;

	GetEpochRequestMessage() {
		// Serializer only
		super(0);
		this.author = null;
		this.epoch = 0;
	}

	GetEpochRequestMessage(ECPublicKey author, int magic, long epoch) {
		super(magic);
		this.author = author;
		this.epoch = epoch;
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

	public long getEpoch() {
		return epoch;
	}

	@Override
	public String toString() {
		return String.format("%s{author=%s epoch=%s}", getClass().getSimpleName(), author, epoch);
	}
}
