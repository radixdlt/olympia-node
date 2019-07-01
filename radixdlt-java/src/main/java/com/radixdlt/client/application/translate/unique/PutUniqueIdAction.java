package com.radixdlt.client.application.translate.unique;

import com.radixdlt.client.application.translate.Action;
import com.radixdlt.client.atommodel.accounts.RadixAddress;
import java.util.Objects;

public class PutUniqueIdAction implements Action {

	/**
	 * Address for uniqueness constraint
	 */
	private final RadixAddress address;

	/**
	 * Unique identifier
	 */
	private final String unique;

	public PutUniqueIdAction(RadixAddress address, String unique) {
		Objects.requireNonNull(address);
		Objects.requireNonNull(unique);

		this.address = address;
		this.unique = unique;
	}

	public static PutUniqueIdAction create(RadixAddress address, String unique) {
		return new PutUniqueIdAction(address, unique);
	}

	public RadixAddress getAddress() {
		return address;
	}

	public String getUnique() {
		return unique;
	}
}
