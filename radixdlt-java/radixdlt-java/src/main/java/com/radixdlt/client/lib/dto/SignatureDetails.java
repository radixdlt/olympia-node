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
import com.radixdlt.client.lib.api.ValidatorAddress;

import java.util.Objects;

public class SignatureDetails {
	private final ValidatorAddress address;
	private final String signature;
	private final long timestamp;

	private SignatureDetails(ValidatorAddress address, String signature, long timestamp) {
		this.address = address;
		this.signature = signature;
		this.timestamp = timestamp;
	}

	@JsonCreator
	public static SignatureDetails create(
		@JsonProperty(value = "address", required = true) ValidatorAddress address,
		@JsonProperty(value = "signature", required = true) String signature,
		@JsonProperty(value = "timestamp", required = true) long timestamp
	) {
		return new SignatureDetails(address, signature, timestamp);
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
		return timestamp == that.timestamp && address.equals(that.address) && signature.equals(that.signature);
	}

	@Override
	public int hashCode() {
		return Objects.hash(address, signature, timestamp);
	}

	@Override
	public String toString() {
		return "{address:" + address
			+ ", signature:" + signature
			+ ", timestamp:" + timestamp + '}';
	}

	public ValidatorAddress getAddress() {
		return address;
	}

	public String getSignature() {
		return signature;
	}

	public long getTimestamp() {
		return timestamp;
	}
}
