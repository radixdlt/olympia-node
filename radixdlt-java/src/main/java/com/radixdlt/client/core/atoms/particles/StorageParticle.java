package com.radixdlt.client.core.atoms.particles;

import com.radixdlt.client.core.address.RadixAddress;
import com.radixdlt.client.core.atoms.MetadataMap;
import com.radixdlt.client.core.atoms.particles.quarks.AddressableQuark;
import com.radixdlt.client.core.atoms.particles.quarks.DataQuark;
import com.radixdlt.client.core.crypto.ECPublicKey;
import org.radix.serialization2.SerializerId2;

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
@SerializerId2("STORAGEPARTICLE")
public class StorageParticle extends Particle {
	public static class StorageParticleBuilder {
		private final List<RadixAddress> addresses = new ArrayList<>();
		private final MetadataMap metaData = new MetadataMap();
		private byte[] bytes;
		public StorageParticleBuilder setMetaData(String key, String value) {
			metaData.put(key, value);
			return this;
		}

		public StorageParticleBuilder payload(byte[] bytes) {
			this.bytes = bytes;
			return this;
		}

		public StorageParticleBuilder accounts(Collection<RadixAddress> addresses) {
			addresses.forEach(this::account);
			return this;
		}

		public StorageParticleBuilder account(RadixAddress address) {
			addresses.add(address);
			return this;
		}

		public StorageParticle build() {
			return new StorageParticle(bytes, metaData.isEmpty() ? null : metaData, addresses);
		}
	}

	private StorageParticle() {
	}

	private StorageParticle(byte[] bytes, MetadataMap metaData, List<RadixAddress> addresses) {
		super(new AddressableQuark(addresses), new DataQuark(bytes, metaData));
		Objects.requireNonNull(bytes);
	}

	@Override
	public Set<ECPublicKey> getAddresses() {
		return getQuarkOrError(AddressableQuark.class).getAddresses().stream().map(RadixAddress::getPublicKey).collect(Collectors.toSet());
	}

	public String getMetaData(String key) {
		Map<String, String> metaData = this.getQuarkOrError(DataQuark.class).getMetaData();

		if (metaData == null) {
			return null;
		}

		return metaData.get(key);
	}
}
