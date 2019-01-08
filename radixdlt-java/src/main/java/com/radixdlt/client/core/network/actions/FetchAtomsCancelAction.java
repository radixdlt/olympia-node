package com.radixdlt.client.core.network.actions;

import com.radixdlt.client.atommodel.accounts.RadixAddress;
import com.radixdlt.client.core.network.RadixNode;
import java.util.Objects;

/**
 * A dispatchable fetch atoms action which represents a request to cancel a fetch atoms flow
 */
public final class FetchAtomsCancelAction implements FetchAtomsAction {
	private final String uuid;
	private final RadixAddress address;

	private FetchAtomsCancelAction(String uuid, RadixAddress address) {
		Objects.requireNonNull(uuid);
		Objects.requireNonNull(address);

		this.uuid = uuid;
		this.address = address;
	}

	public static FetchAtomsCancelAction of(String uuid, RadixAddress address) {
		return new FetchAtomsCancelAction(uuid, address);
	}

	@Override
	public String getUuid() {
		return uuid;
	}

	@Override
	public RadixAddress getAddress() {
		return address;
	}

	// TODO: Get rid of this method. Maybe create a new RadixNetworkAction interface?
	@Override
	public RadixNode getNode() {
		throw new UnsupportedOperationException();
	}

	@Override
	public String toString() {
		return "FETCH_ATOMS_CANCEL " + uuid + " " + address;
	}
}
