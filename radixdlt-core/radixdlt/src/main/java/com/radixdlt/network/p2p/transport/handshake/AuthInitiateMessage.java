/*
 * (C) Copyright 2021 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 */

package com.radixdlt.network.p2p.transport.handshake;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.hash.HashCode;
import com.radixdlt.crypto.ECDSASignature;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.SerializerConstants;
import com.radixdlt.serialization.SerializerDummy;
import com.radixdlt.serialization.SerializerId2;

import java.util.Objects;

@SerializerId2("message.handshake.auth_initiate")
public final class AuthInitiateMessage {

	@JsonProperty(SerializerConstants.SERIALIZER_NAME)
	@DsonOutput(DsonOutput.Output.ALL)
	private SerializerDummy serializer = SerializerDummy.DUMMY;

	@JsonProperty("signature")
	@DsonOutput(DsonOutput.Output.ALL)
	private final ECDSASignature signature;

	@JsonProperty("publicKey")
	@DsonOutput(DsonOutput.Output.ALL)
	private final HashCode publicKey;

	@JsonProperty("nonce")
	@DsonOutput(DsonOutput.Output.ALL)
	private final HashCode nonce;

	@JsonCreator
	public static AuthInitiateMessage deserialize(
		@JsonProperty("signature") ECDSASignature signature,
		@JsonProperty("publicKey") HashCode publicKey,
		@JsonProperty("nonce") HashCode nonce
	) {
		return new AuthInitiateMessage(signature, publicKey, nonce);
	}

	public AuthInitiateMessage(ECDSASignature signature, HashCode publicKey, HashCode nonce) {
		this.signature = signature;
		this.publicKey = publicKey;
		this.nonce = nonce;
	}

	public ECDSASignature getSignature() {
		return signature;
	}

	public HashCode getPublicKey() {
		return publicKey;
	}

	public HashCode getNonce() {
		return nonce;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		final var that = (AuthInitiateMessage) o;
		return Objects.equals(signature, that.signature)
			&& Objects.equals(publicKey, that.publicKey)
			&& Objects.equals(nonce, that.nonce);
	}

	@Override
	public int hashCode() {
		return Objects.hash(signature, publicKey, nonce);
	}
}
