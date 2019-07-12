package com.radixdlt.atomos;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * A Radix resource identifier is a human readable getDestinations into the Ledger which points to a unique UP particle.
 *
 * TODO: Map this to a unique string e.g. address/getDestinations/:type/:name OR shardableType/:shardable/getDestinations/:type/:name
 */
public final class RRI {
	private static final String NAME_REGEX = "[-0-9A-Za-z]+";
	private static final Pattern NAME_PATTERN = Pattern.compile(NAME_REGEX);

	// TODO: Will replace this with shardable at some point
	private final RadixAddress address;
	private final String name;

	RRI(RadixAddress address, String name) {
		this.address = Objects.requireNonNull(address);
		this.name = Objects.requireNonNull(name);
	}

	public RadixAddress getAddress() {
		return address;
	}

	public String getName() {
		return name;
	}

	public static RRI of(RadixAddress address, String name) {
		return new RRI(address, name);
	}

	public static RRI from(String s) {
		String[] split = s.split("/", 3);
		if (split.length != 3 || split[0].length() != 0) {
			throw new IllegalArgumentException("RRI does not have enough components and must be of the format /:address/:name (" + s + ")");
		}

		RadixAddress address = RadixAddress.from(split[1]);
		String name = split[2];

		if (!NAME_PATTERN.matcher(name).matches()) {
			throw new IllegalArgumentException("RRI name invalid, must match regex '" + NAME_REGEX + "': " + s);
		}

		return new RRI(address, name);
	}

	@Override
	public String toString() {
		return "/" + address.toString() + "/" + name;
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof RRI)) {
			return false;
		}

		RRI rri = (RRI) o;
		return Objects.equals(rri.address, address) && Objects.equals(rri.name, name);
	}

	@Override
	public int hashCode() {
		return Objects.hash(address, name);
	}
}