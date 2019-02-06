package com.radixdlt.client.atommodel.message;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.radixdlt.client.atommodel.accounts.RadixAddress;
import com.radixdlt.client.atommodel.quarks.AccountableQuark;
import com.radixdlt.client.core.atoms.particles.Particle;
import java.util.TreeMap;
import org.radix.serialization2.DsonOutput;
import org.radix.serialization2.SerializerId2;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Particle which can hold arbitrary data
 */
@SerializerId2("MESSAGEPARTICLE")
public class MessageParticle extends Particle {
	public static class MessageParticleBuilder {
		private RadixAddress from;
		private RadixAddress to;
		private final MetadataMap metaData = new MetadataMap();
		private byte[] bytes;

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

		public MessageParticle build() {
			return new MessageParticle(this.from, this.bytes, this.metaData, Arrays.asList(this.from, this.to));
		}
	}

	private MessageParticle() {
	}

	@JsonProperty("from")
	@DsonOutput(DsonOutput.Output.ALL)
	private RadixAddress from;

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

	private MessageParticle(RadixAddress from, byte[] bytes, MetadataMap metaData, List<RadixAddress> addresses) {
		super(new AccountableQuark(addresses));
		Objects.requireNonNull(bytes);

		this.from = Objects.requireNonNull(from, "from is required");
		this.bytes = Arrays.copyOf(bytes, bytes.length);
		this.metaData.putAll(metaData);
	}

	public String getMetaData(String key) {
		return metaData.get(key);
	}

	public RadixAddress getFrom() {
		return this.from;
	}

	public byte[] getBytes() {
		return bytes;
	}
}
