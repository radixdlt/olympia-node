package com.radixdlt.client.atommodel.message;

import com.radixdlt.client.atommodel.accounts.RadixAddress;
import com.radixdlt.client.atommodel.quarks.AccountableQuark;
import com.radixdlt.client.atommodel.quarks.DataQuark;
import com.radixdlt.client.core.atoms.particles.Particle;
import com.radixdlt.client.core.crypto.ECPublicKey;
import org.radix.serialization2.SerializerId2;
import sun.jvm.hotspot.debugger.Address;

import java.util.ArrayList;
import java.util.Collection;
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
		private RadixAddress source;
		private final List<RadixAddress> addresses = new ArrayList<>();
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

		public MessageParticleBuilder accounts(Collection<RadixAddress> addresses) {
			addresses.forEach(this::account);
			return this;
		}

		public MessageParticleBuilder account(RadixAddress address) {
			addresses.add(address);
			return this;
		}

		public MessageParticleBuilder source(RadixAddress source) {
			this.source = source;
			return this;
		}

		public MessageParticle build() {
			return new MessageParticle(source, bytes, metaData.isEmpty() ? null : metaData, addresses);
		}
	}

	private MessageParticle() {
	}

	private RadixAddress source;

	private MessageParticle(RadixAddress source, byte[] bytes, MetadataMap metaData, List<RadixAddress> addresses) {
		super(new AccountableQuark(addresses), new DataQuark(bytes, metaData));
		Objects.requireNonNull(bytes);

		this.source = Objects.requireNonNull(source, "source is required");
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

	public RadixAddress getSource() {
		return source;
	}
}
