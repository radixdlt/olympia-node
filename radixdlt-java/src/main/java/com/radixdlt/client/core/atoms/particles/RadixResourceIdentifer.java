package com.radixdlt.client.core.atoms.particles;

import com.radixdlt.client.atommodel.accounts.RadixAddress;
import java.util.Objects;

/**
 * A Radix resource identifier is a human readable index into the Ledger which points to a unique UP particle.
 *
 * TODO: Map this to a unique string e.g. address/index/:type/:name OR shardableType/:shardable/index/:type/:name
 */
public class RadixResourceIdentifer {
	// TODO: Will replace this with shardable at some point
	private RadixAddress address;
	private String unique;
	private String type;

	public RadixResourceIdentifer(RadixAddress address, String type, String unique) {
		this.address = address;
		this.unique = unique;
		this.type = type;
	}

	public RadixAddress getAddress() {
		return address;
	}

	public String getType() {
		return type;
	}

	public String getUnique() {
		return unique;
	}

	public static RadixResourceIdentifer fromString(String s) {
		String[] split = s.split("/");
		if (split.length < 4) {
			throw new IllegalArgumentException("RRI must be of the format /:address/:type/:unique");
		}

		RadixAddress address = RadixAddress.from(split[1]);
		String type = split[2];
		String unique = s.substring(split[1].length() + split[2].length() + 3);

		return new RadixResourceIdentifer(address, type, unique);
	}

	@Override
	public String toString() {
		return "/" + address.toString() + "/" + type + "/" + unique;
	}

	@Override
	public int hashCode() {
		return Objects.hash(address, unique, type);
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof RadixResourceIdentifer)) {
			return false;
		}

		RadixResourceIdentifer rri = (RadixResourceIdentifer) o;
		return Objects.equals(address, rri.address)
			&& Objects.equals(unique, rri.unique)
			&& Objects.equals(type, rri.type);
	}
}
