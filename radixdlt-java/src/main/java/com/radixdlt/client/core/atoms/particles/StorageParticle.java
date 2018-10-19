package com.radixdlt.client.core.atoms.particles;

import com.radixdlt.client.core.address.RadixAddress;
import com.radixdlt.client.core.atoms.AccountReference;
import com.radixdlt.client.core.atoms.MetadataMap;
import com.radixdlt.client.core.atoms.particles.quarks.AddressableQuark;
import com.radixdlt.client.core.atoms.particles.quarks.DataQuark;
import com.radixdlt.client.core.crypto.ECPublicKey;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Particle which can hold arbitrary data
 */
public class StorageParticle extends Particle {
	public static class StorageParticleBuilder {

		private final List<AccountReference> addresses = new ArrayList<>();
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
			addresses.add(new AccountReference(address.getPublicKey()));
			return this;
		}

		public StorageParticle build() {
			return new StorageParticle(bytes, metaData.isEmpty() ? null : metaData, addresses);
		}

	}

	private StorageParticle(byte[] bytes, MetadataMap metaData, List<AccountReference> addresses) {
		super(Spin.UP, new AddressableQuark(addresses), new DataQuark(bytes, metaData));
		Objects.requireNonNull(bytes);
	}

	@Override
	public Set<ECPublicKey> getAddresses() {
		return getQuarkOrError(AddressableQuark.class).getAddresses().stream().map(AccountReference::getKey).collect(Collectors.toSet());
	}

	public String getMetaData(String key) {
		Map<String, String> metaData = this.getQuarkOrError(DataQuark.class).getMetaData();

		if (metaData == null) {
			return null;
		}

		return metaData.get(key);
	}
}
