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

package com.radixdlt.client.lib.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.crypto.exception.PublicKeyException;

import java.util.Objects;

public class SignatureDetails {
	private final ECPublicKey key;
	private final String signature;
	private final long timestamp;

	private SignatureDetails(ECPublicKey key, String signature, long timestamp) {
		this.key = key;
		this.signature = signature;
		this.timestamp = timestamp;
	}

	@JsonCreator
	public static SignatureDetails create(
		@JsonProperty(value = "key", required = true) String key,
		@JsonProperty(value = "signature", required = true) String signature,
		@JsonProperty(value = "timestamp", required = true) long timestamp
	) throws PublicKeyException {
		return new SignatureDetails(ECPublicKey.fromHex(key), signature, timestamp);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}

		if (!(o instanceof SignatureDetails)) {
			return false;
		}

		var that = (SignatureDetails) o;
		return timestamp == that.timestamp && key.equals(that.key) && signature.equals(that.signature);
	}

	@Override
	public int hashCode() {
		return Objects.hash(key, signature, timestamp);
	}

	@Override
	public String toString() {
		return "{key:" + key.toHex()
			+ ", signature:" + signature
			+ ", timestamp:" + timestamp + '}';
	}

	public ECPublicKey getKey() {
		return key;
	}

	public String getSignature() {
		return signature;
	}

	public long getTimestamp() {
		return timestamp;
	}
}
