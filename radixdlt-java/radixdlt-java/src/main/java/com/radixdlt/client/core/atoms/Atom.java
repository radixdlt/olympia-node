/*
 * (C) Copyright 2021 Radix DLT Ltd
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
 *
 */

package com.radixdlt.client.core.atoms;

import org.bouncycastle.util.encoders.Hex;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.radixdlt.DefaultSerialization;
import com.radixdlt.crypto.ECDSASignature;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.SerializerConstants;
import com.radixdlt.serialization.SerializerDummy;
import com.radixdlt.serialization.SerializerId2;

import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

import javax.annotation.concurrent.Immutable;

/**
 * An atom to be processed by radix engine
 */
@Immutable
@SerializerId2("atom")
public final class Atom {
	@JsonProperty(SerializerConstants.SERIALIZER_NAME)
	@DsonOutput(value = {Output.API, Output.WIRE, Output.PERSIST})
	SerializerDummy serializer = SerializerDummy.DUMMY;

	@JsonProperty("i")
	@DsonOutput({Output.ALL})
	private final byte[] unsignedBlob;

	@JsonProperty("s")
	@DsonOutput({Output.ALL})
	private final ECDSASignature signature;

	@JsonCreator
	private Atom(
		@JsonProperty("i") byte[] unsignedBlob,
		@JsonProperty("s") ECDSASignature signature
	) {
		this.unsignedBlob = unsignedBlob;
		this.signature = signature;
	}

	public static Atom create(
		byte[] unsignedBlob,
		ECDSASignature signature
	) {
		return new Atom(unsignedBlob, signature);
	}

	public byte[] getUnsignedBlob() {
		return unsignedBlob;
	}

	public Optional<ECDSASignature> getSignature() {
		return Optional.ofNullable(signature);
	}

	@Override
	public int hashCode() {
		return Objects.hash(signature, unsignedBlob);
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof Atom)) {
			return false;
		}

		var other = (Atom) o;
		var thisDson = DefaultSerialization.getInstance().toDson(this, Output.ALL);
		var otherDson = DefaultSerialization.getInstance().toDson(other, Output.ALL);
		return Arrays.equals(thisDson, otherDson);
	}

	@Override
	public String toString() {
		return String.format("%s {blob=%s}",
			this.getClass().getSimpleName(),
			Hex.toHexString(unsignedBlob)
		);
	}
}
