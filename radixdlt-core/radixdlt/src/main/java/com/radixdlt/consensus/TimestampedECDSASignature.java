/*
 * (C) Copyright 2020 Radix DLT Ltd
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

package com.radixdlt.consensus;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.radixdlt.crypto.ECDSASignature;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.SerializerConstants;
import com.radixdlt.serialization.SerializerDummy;
import com.radixdlt.serialization.SerializerId2;

import java.util.Objects;
import javax.annotation.concurrent.Immutable;

/**
 * An <a href="https://en.wikipedia.org/wiki/Elliptic_Curve_Digital_Signature_Algorithm">ECDSA</a>
 * signature, together with a timestamp and a weighting for that timestamp.
 */
@Immutable
@SerializerId2("consensus.timestamped_ecdsa_signature")
public final class TimestampedECDSASignature {
	// Placeholder for the serializer ID
	@JsonProperty(SerializerConstants.SERIALIZER_NAME)
	@DsonOutput(Output.ALL)
	private SerializerDummy serializer = SerializerDummy.DUMMY;

	@JsonProperty("timestamp")
	@DsonOutput(Output.ALL)
	private final long timestamp;

	@JsonProperty("signature")
	@DsonOutput(Output.ALL)
	private final ECDSASignature signature;

	/**
	 * Create a timestamped signature from a timestamp, weight and signature.
	 *
	 * @param timestamp the timestamp
	 * @param signature the signature
	 * @return a timestamped signature with the specified properties
	 */
	@JsonCreator
	public static TimestampedECDSASignature from(
		@JsonProperty("timestamp") long timestamp,
		@JsonProperty("signature") ECDSASignature signature
	) {
		return new TimestampedECDSASignature(timestamp, signature);
	}

	private TimestampedECDSASignature(long timestamp, ECDSASignature signature) {
		this.timestamp = timestamp;
		this.signature = Objects.requireNonNull(signature);
	}

	/**
	 * Returns the timestamp of the signature in milliseconds since epoch.
	 * @return The timestamp of the signature in milliseconds since epoch
	 */
	public long timestamp() {
		return this.timestamp;
	}

	/**
	 * Returns the signature.
	 * @return the signature
	 */
	public ECDSASignature signature() {
		return this.signature;
	}

	@Override
	public boolean equals(Object o) {
		if (o instanceof TimestampedECDSASignature) {
			TimestampedECDSASignature that = (TimestampedECDSASignature) o;
			return this.timestamp == that.timestamp
				&& Objects.equals(this.signature, that.signature);
		}
		return false;
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.timestamp, this.signature);
	}

	@Override
	public String toString() {
		return String.format("%s[%s:%s]", getClass().getSimpleName(), this.timestamp, this.signature);
	}
}