package com.radixdlt.client.core.atoms.particles;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.radix.serialization2.DsonOutput;
import org.radix.serialization2.DsonOutput.Output;
import org.radix.serialization2.SerializerId2;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.radixdlt.client.core.address.RadixAddress;
import com.radixdlt.client.core.atoms.AccountReference;
import com.radixdlt.client.core.atoms.MetadataMap;
import com.radixdlt.client.core.atoms.Payload;
import com.radixdlt.client.core.crypto.ECPublicKey;

/**
 * Particle which can hold arbitrary data
 */
@SerializerId2("DATAPARTICLE")
public class DataParticle extends Particle {
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

	@JsonProperty("addresses")
	@DsonOutput(Output.ALL)
	private List<AccountReference> addresses;

	/**
	 * Nullable for the timebeing as we want dson to be optimized for
	 * saving space and no way to skip empty maps in Dson yet.
	 */
	@JsonProperty("metaData")
	@DsonOutput(Output.ALL)
	private MetadataMap metaData;

	/**
	 * Arbitrary data, possibly encrypted
	 */
	@JsonProperty("bytes")
	@DsonOutput(Output.ALL)
	private  Payload bytes;

	private Spin spin;

	DataParticle() {
		// No-arg constructor for serializer
	}

	private DataParticle(Payload bytes, MetadataMap metaData, List<AccountReference> addresses) {
		Objects.requireNonNull(bytes);

		this.spin = Spin.UP;
		this.bytes = bytes;
		this.metaData = metaData;
		this.addresses = addresses;
	}

	@Override
	public Set<ECPublicKey> getAddresses() {
		return addresses.stream().map(AccountReference::getKey).collect(Collectors.toSet());
	}

	@Override
	public Spin getSpin() {
		return spin;
	}

	public Object getMetaData(String key) {
		if (metaData == null) {
			return null;
		}

		return metaData.get(key);
	}

	public Payload getBytes() {
		return bytes;
	}

	@JsonProperty("spin")
	@DsonOutput(value = {Output.WIRE, Output.API, Output.PERSIST})
	private int getJsonSpin() {
		return this.spin.ordinalValue();
	}

	@JsonProperty("spin")
	private void setJsonSpin(int spin) {
		this.spin = Spin.valueOf(spin);
	}
}
