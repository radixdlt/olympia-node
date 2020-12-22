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

package com.radixdlt.atommodel.message;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableSet;
import com.radixdlt.identifiers.RadixAddress;
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.SerializerId2;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

/**
 * A simple particle for messages which get stored in one to two addresses.
 */
@SerializerId2("radix.particles.message")
public final class MessageParticle extends Particle {
	@JsonProperty("from")
	@DsonOutput(DsonOutput.Output.ALL)
	private RadixAddress from;

	@JsonProperty("to")
	@DsonOutput(DsonOutput.Output.ALL)
	private RadixAddress to;

	/**
	 * Metadata, aka data about the data (e.g. contentType).
	 * Will consider down the line whether this is worth putting
	 * into a more concrete class (e.g. MetaData.java).
	 */
	@JsonProperty("metaData")
	@DsonOutput(DsonOutput.Output.ALL)
	private Map<String, String> metaData = new TreeMap<>();

	/**
	 * Arbitrary data
	 */
	@JsonProperty("bytes")
	@DsonOutput(DsonOutput.Output.ALL)
	private byte[] bytes;

	@JsonProperty("nonce")
	@DsonOutput(DsonOutput.Output.ALL)
	private long nonce;

	MessageParticle() {
		// Serializer only
		super(ImmutableSet.of());
	}

	public MessageParticle(RadixAddress from, RadixAddress to, byte[] bytes, long nonce) {
		super(ImmutableSet.of(from.euid(), to.euid()));

		this.from = Objects.requireNonNull(from, "from is required");
		this.to = Objects.requireNonNull(to, "to is required");
		this.bytes = Arrays.copyOf(bytes, bytes.length);
		this.nonce = nonce;
	}

	public MessageParticle(RadixAddress from, RadixAddress to, byte[] bytes) {
		super(ImmutableSet.of(from.euid(), to.euid()));

		this.from = Objects.requireNonNull(from, "from is required");
		this.to = Objects.requireNonNull(to, "to is required");
		this.bytes = Arrays.copyOf(bytes, bytes.length);
		this.nonce = System.nanoTime();
	}

	public MessageParticle(RadixAddress from, RadixAddress to, byte[] bytes, String contentType) {
		super(ImmutableSet.of(from.euid(), to.euid()));

		this.from = Objects.requireNonNull(from, "from is required");
		this.to = Objects.requireNonNull(to, "to is required");
		this.bytes = Arrays.copyOf(bytes, bytes.length);
		this.metaData.put("contentType", contentType);
		this.nonce = System.nanoTime();
	}

	Set<RadixAddress> getAddresses() {
		return ImmutableSet.of(from, to);
	}


	public long getNonce() {
		return nonce;
	}

	public RadixAddress getFrom() {
		return from;
	}

	public RadixAddress getTo() {
		return to;
	}

	public byte[] getBytes() {
		return bytes;
	}

	public Map<String, String> getMetaData() {
		return metaData;
	}

	@Override
	public String toString() {
		return String.format("%s[(%s:%s)]",
			getClass().getSimpleName(), String.valueOf(from), String.valueOf(to));
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof MessageParticle)) {
			return false;
		}
		MessageParticle that = (MessageParticle) o;
		return nonce == that.nonce
				&& Objects.equals(from, that.from)
				&& Objects.equals(to, that.to)
				&& Objects.equals(metaData, that.metaData)
				&& Arrays.equals(bytes, that.bytes)
				&& Objects.equals(getDestinations(), that.getDestinations());
	}

	@Override
	public int hashCode() {
		int result = Objects.hash(from, to, metaData, nonce, getDestinations());
		result = 31 * result + Arrays.hashCode(bytes);
		return result;
	}
}
