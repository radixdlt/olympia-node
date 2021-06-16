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
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.SerializerConstants;
import com.radixdlt.serialization.SerializerDummy;
import com.radixdlt.serialization.SerializerId2;

import java.util.Objects;

@SerializerId2("message.handshake.auth_response")
public final class AuthResponseMessage {

	@JsonProperty(SerializerConstants.SERIALIZER_NAME)
	@DsonOutput(DsonOutput.Output.ALL)
	private SerializerDummy serializer = SerializerDummy.DUMMY;

	@JsonProperty("ephemeralPublicKey")
	@DsonOutput(DsonOutput.Output.ALL)
	private final HashCode ephemeralPublicKey;

	@JsonProperty("nonce")
	@DsonOutput(DsonOutput.Output.ALL)
	private final HashCode nonce;

	@JsonCreator
	public static AuthResponseMessage deserialize(
		@JsonProperty("ephemeralPublicKey") HashCode ephemeralPublicKey,
		@JsonProperty("nonce") HashCode nonce
	) {
		return new AuthResponseMessage(ephemeralPublicKey, nonce);
	}

	public AuthResponseMessage(HashCode ephemeralPublicKey, HashCode nonce) {
		this.ephemeralPublicKey = ephemeralPublicKey;
		this.nonce = nonce;
	}

	public HashCode getEphemeralPublicKey() {
		return ephemeralPublicKey;
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
		final var that = (AuthResponseMessage) o;
		return Objects.equals(ephemeralPublicKey, that.ephemeralPublicKey) && Objects.equals(nonce, that.nonce);
	}

	@Override
	public int hashCode() {
		return Objects.hash(ephemeralPublicKey, nonce);
	}
}
