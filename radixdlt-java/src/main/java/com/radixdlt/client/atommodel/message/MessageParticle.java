package com.radixdlt.client.atommodel.message;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.radixdlt.client.atommodel.accounts.RadixAddress;
import com.radixdlt.client.atommodel.quarks.AccountableQuark;
import com.radixdlt.client.atommodel.quarks.DataQuark;
import com.radixdlt.client.core.atoms.particles.Particle;
import com.radixdlt.client.core.crypto.ECPublicKey;
import org.radix.serialization2.DsonOutput;
import org.radix.serialization2.SerializerId2;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

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
		public MessageParticleBuilder setMetaData(String key, String value) {
			metaData.put(key, value);
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
			return new MessageParticle(from, bytes, metaData, Arrays.asList(from, to));
		}
	}

	private MessageParticle() {
	}

	@JsonProperty("from")
	@DsonOutput(DsonOutput.Output.ALL)
	private RadixAddress from;

	private MessageParticle(RadixAddress from, byte[] bytes, MetadataMap metaData, List<RadixAddress> addresses) {
		super(new AccountableQuark(addresses), new DataQuark(bytes, metaData));
		Objects.requireNonNull(bytes);

		this.from = Objects.requireNonNull(from, "from is required");
	}

	@Override
	public Set<ECPublicKey> getAddresses() {
		return getQuarkOrError(AccountableQuark.class).getAddresses().stream().map(RadixAddress::getPublicKey).collect(Collectors.toSet());
	}

	public String getMetaData(String key) {
		Map<String, String> metaData = this.getQuarkOrError(DataQuark.class).getMetaData();

		if (metaData == null) {
			return null;
		}

		return metaData.get(key);
	}

	public RadixAddress getFrom() {
		return from;
	}
}
