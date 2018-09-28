package com.radixdlt.client.core.atoms;

import com.radixdlt.client.core.address.EUID;
import com.radixdlt.client.core.address.RadixAddress;
import com.radixdlt.client.core.crypto.ECPublicKey;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Particle which can hold arbitrary data
 */
public class DataParticle implements Particle {
	public static class DataParticleBuilder {
		private final List<AccountReference> addresses = new ArrayList<>();
		private final MetadataMap metaData = new MetadataMap();
		private Payload bytes;

		public DataParticleBuilder setMetaData(String key, String value) {
			metaData.put(key, value);
			return this;
		}

		public DataParticleBuilder payload(Payload bytes) {
			this.bytes = bytes;
			return this;
		}

		public DataParticleBuilder accounts(Collection<RadixAddress> addresses) {
			addresses.forEach(this::account);
			return this;
		}

		public DataParticleBuilder account(RadixAddress address) {
			addresses.add(new AccountReference(address.getPublicKey()));
			return this;
		}

		public DataParticle build() {
			return new DataParticle(bytes, metaData.isEmpty() ? null : metaData, addresses);
		}
	}

	private final List<AccountReference> addresses;

	/**
	 * Nullable for the timebeing as we want dson to be optimized for
	 * saving space and no way to skip empty maps in Dson yet.
	 */
	private final MetadataMap metaData;

	/**
	 * Arbitrary data, possibly encrypted
	 */
	private final Payload bytes;

	private final long spin;

	private DataParticle(Payload bytes, MetadataMap metaData, List<AccountReference> addresses) {
		Objects.requireNonNull(bytes);

		this.spin = 1;
		this.bytes = bytes;
		this.metaData = metaData;
		this.addresses = addresses;
	}

	public Set<EUID> getDestinations() {
		return addresses.stream().map(AccountReference::getKey).map(ECPublicKey::getUID).collect(Collectors.toSet());
	}

	public Object getMetaData(String key) {
		if (metaData == null) {
			return null;
		}

		return metaData.get(key);
	}

	public long getSpin() {
		return spin;
	}

	public Payload getBytes() {
		return bytes;
	}
}
