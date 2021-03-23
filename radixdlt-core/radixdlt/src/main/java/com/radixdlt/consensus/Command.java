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

package com.radixdlt.consensus;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.radixdlt.crypto.HashUtils;
import com.radixdlt.identifiers.AID;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.SerializerConstants;
import com.radixdlt.serialization.SerializerDummy;
import com.radixdlt.serialization.SerializerId2;
import com.radixdlt.utils.Bytes;

import java.util.Objects;
import java.util.function.Function;
import javax.annotation.concurrent.Immutable;

/**
 * Generic application command
 */
@Immutable
@SerializerId2("consensus.command")
public final class Command {
	@JsonProperty(SerializerConstants.SERIALIZER_NAME)
	@DsonOutput(value = {Output.API, Output.WIRE, Output.PERSIST})
	SerializerDummy serializer = SerializerDummy.DUMMY;

	@JsonProperty("payload")
	@DsonOutput(Output.ALL)
	private final byte[] payload;
	private final AID atomId;

	@JsonCreator
	public Command(@JsonProperty("payload") byte[] payload) {
		this.payload = Objects.requireNonNull(payload);
		var firstHash = HashUtils.sha256(payload);
		this.atomId = AID.from(HashUtils.sha256(firstHash.asBytes()).asBytes());
	}

	public <T> T map(Function<byte[], T> mapper) {
		return mapper.apply(payload);
	}

	public AID getAtomId() {
		return atomId;
	}

	public byte[] getPayload() {
		return payload;
	}

	@Override
	public int hashCode() {
		return Objects.hash(atomId);
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof Command)) {
			return false;
		}

		Command other = (Command) o;
		return Objects.equals(this.atomId, other.atomId);
	}

	@Override
	public String toString() {
		String payloadHashStr = Bytes.toHexString(payload);
		return String.format("%s{payload=%s...}",
				this.getClass().getSimpleName(),
				payloadHashStr.substring(0, Math.min(20, payloadHashStr.length())));
	}
}
