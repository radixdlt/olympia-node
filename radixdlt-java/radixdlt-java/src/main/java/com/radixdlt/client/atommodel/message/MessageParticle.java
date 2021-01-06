/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Permission is hereby granted, free of charge, to any person obtaining a
 * copy of this software and associated documentation files (the “Software”),
 * to deal in the Software without restriction, including without limitation
 * the rights to use, copy, modify, merge, publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS
 * OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL
 * THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER
 * DEALINGS IN THE SOFTWARE.
 */

package com.radixdlt.client.atommodel.message;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableSet;
import com.radixdlt.client.atommodel.Accountable;
import com.radixdlt.identifiers.RadixAddress;
import com.radixdlt.client.core.atoms.particles.Particle;

import java.util.Random;
import java.util.Set;
import java.util.TreeMap;

import com.radixdlt.identifiers.EUID;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.SerializerId2;

import java.util.Arrays;
import java.util.Map;
import java.util.Objects;

/**
 * Particle which can hold arbitrary data
 */
@SerializerId2("radix.particles.message")
public final class MessageParticle extends Particle implements Accountable {
	public static final class MessageParticleBuilder {
		private static final Random RNG = new Random();
		private RadixAddress from;
		private RadixAddress to;
		private final MetadataMap metaData = new MetadataMap();
		private byte[] bytes;
		private long nonce = RNG.nextLong();

		public MessageParticleBuilder metaData(String key, String value) {
			this.metaData.put(key, value);
			return this;
		}

		public MessageParticleBuilder payload(byte[] bytes) {
			this.bytes = bytes;
			return this;
		}

		public MessageParticleBuilder to(RadixAddress to) {
			this.to = to;
			return this;
		}

		public MessageParticleBuilder from(RadixAddress from) {
			this.from = from;
			return this;
		}

		public MessageParticleBuilder nonce(long nonce) {
			this.nonce = nonce;
			return this;
		}

		public MessageParticle build() {
			return new MessageParticle(this.from, this.to, this.bytes, this.metaData, nonce);
		}
	}

	public static MessageParticleBuilder builder() {
		return new MessageParticleBuilder();
	}

	@JsonProperty("from")
	@DsonOutput(DsonOutput.Output.ALL)
	private final RadixAddress from;


	@JsonProperty("to")
	@DsonOutput(DsonOutput.Output.ALL)
	private final RadixAddress to;

	/**
	 * Metadata, aka data about the data (e.g. contentType).
	 * Will consider down the line whether this is worth putting
	 * into a more concrete class (e.g. MetaData.java).
	 */
	@JsonProperty("metaData")
	@DsonOutput(DsonOutput.Output.ALL)
	private final Map<String, String> metaData = new TreeMap<>();

	/**
	 * Arbitrary data
	 */
	@JsonProperty("bytes")
	@DsonOutput(DsonOutput.Output.ALL)
	private final byte[] bytes;

	/**
	 * Nonce to make every Message unique
	 */
	@JsonProperty("nonce")
	@DsonOutput(DsonOutput.Output.ALL)
	private final long nonce;

	MessageParticle() {
		// Serializer only
		this.from = null;
		this.to = null;
		this.bytes = null;
		this.nonce = 0;
	}

	private MessageParticle(RadixAddress from, RadixAddress to, byte[] bytes, MetadataMap metaData, long nonce) {
		this(from, to, bytes, metaData, nonce, ImmutableSet.of(from.euid(), to.euid()));
	}

	public MessageParticle(
		RadixAddress from,
		RadixAddress to,
		byte[] bytes,
		MetadataMap metaData,
		long nonce,
		ImmutableSet<EUID> destinations
	) {
		super(destinations);

		this.from = Objects.requireNonNull(from, "from is required");
		this.to = Objects.requireNonNull(to, "to is required");
		this.bytes = Arrays.copyOf(bytes, bytes.length);
		if (metaData != null) {
			this.metaData.putAll(metaData);
		}
		this.nonce = nonce;
	}

	@Override
	public Set<RadixAddress> getAddresses() {
		return ImmutableSet.of(from, to);
	}

	public String getMetaData(String key) {
		return metaData.get(key);
	}

	public RadixAddress getFrom() {
		return this.from;
	}

	public RadixAddress getTo() {
		return this.to;
	}

	public byte[] getBytes() {
		return bytes;
	}
}
