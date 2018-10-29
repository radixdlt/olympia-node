package com.radixdlt.client.application.actions;

import com.radixdlt.client.atommodel.accounts.RadixAddress;
import java.util.Objects;

/**
 * A property attached to a transaction which adds the constraint that no
 * other transaction will be accepted by the network with the same unique
 * bytes and address.
 */
public class UniqueProperty {
	private final byte[] unique;
	private final RadixAddress address;

	// TODO: make byte array immutable
	public UniqueProperty(byte[] unique, RadixAddress address) {
		Objects.requireNonNull(unique);
		Objects.requireNonNull(address);

		this.unique = unique;
		this.address = address;
	}

	public byte[] getUnique() {
		return unique;
	}

	public RadixAddress getAddress() {
		return address;
	}
}
