package com.radixdlt.client.application.translate.unique;

import com.radixdlt.identifiers.RadixAddress;
import java.util.Objects;

public final class UniqueId {
	private final RadixAddress address;
	private final String unique;

	public UniqueId(RadixAddress address, String unique) {
		this.address = Objects.requireNonNull(address);
		this.unique = Objects.requireNonNull(unique);
	}

	public RadixAddress getAddress() {
		return address;
	}

	public String getUnique() {
		return unique;
	}

	@Override
	public String toString() {
		return this.getAddress().toString() + "/" + this.getUnique();
	}

	@Override
	public int hashCode() {
		return Objects.hash(address, unique);
	}

	@Override
	public boolean equals(Object obj) {
		if (!(obj instanceof UniqueId)) {
			return false;
		}

		UniqueId uniqueId = (UniqueId) obj;
		return uniqueId.address.equals(this.address) && uniqueId.unique.equals(this.unique);
	}
}
