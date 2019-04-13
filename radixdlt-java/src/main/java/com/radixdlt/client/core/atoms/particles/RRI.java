package com.radixdlt.client.core.atoms.particles;

import com.radixdlt.client.atommodel.accounts.RadixAddress;
import java.util.Objects;

/**
 * A Radix resource identifier is a human readable index into the Ledger which points to a unique state machine
 * instance.
 */
public final class RRI {
	// TODO: Will replace this with shardable at some point
	private RadixAddress address;
	private String unique;

	RRI(RadixAddress address, String unique) {
		this.address = address;
		this.unique = unique;
	}

	public static RRI of(RadixAddress address, String unique) {
		return new RRI(address, unique);
	}

	public RadixAddress getAddress() {
		return address;
	}

	public String getUnique() {
		return unique;
	}

	public static RRI fromString(String s) {
		String[] split = s.split("/");
		if (split.length < 2) {
			throw new IllegalArgumentException("RRI must be of the format /:address/:type/:unique");
		}

		RadixAddress address = RadixAddress.from(split[1]);
		String unique = s.substring(split[1].length() + 2);

		return new RRI(address, unique);
	}

	@Override
	public String toString() {
		return "/" + address.toString() + "/" + unique;
	}

	@Override
	public int hashCode() {
		return Objects.hash(address, unique);
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof RRI)) {
			return false;
		}

		RRI rri = (RRI) o;
		return Objects.equals(address, rri.address)
			&& Objects.equals(unique, rri.unique);
	}
}
