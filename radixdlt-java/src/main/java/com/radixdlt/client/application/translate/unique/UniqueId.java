package com.radixdlt.client.application.translate.unique;

import com.radixdlt.client.atommodel.accounts.RadixAddress;
import java.util.Objects;

public class UniqueId {
	private final RadixAddress address;
	private final String unique;

	public UniqueId(RadixAddress address, String unique) {
		this.address = address;
		this.unique = unique;
	}

	public RadixAddress getAddress() {
		return address;
	}

	public String getUnique() {
		return unique;
	}

	@Override
	public String toString() {
		return this.getAddress().toString() + "/unique/" + this.getUnique();
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
