package com.radixdlt.client.core.network.actions;

import com.radixdlt.client.atommodel.accounts.RadixAddress;
import com.radixdlt.client.core.network.RadixNode;
import java.util.Objects;

/**
 * A dispatchable fetch atoms action which represents a request to cancel a fetch atoms flow
 */
public class FetchAtomsCancelAction implements FetchAtomsAction {
	private final String uuid;
	private final RadixAddress address;

	private FetchAtomsCancelAction(
		String uuid,
		RadixAddress address
	) {
		Objects.requireNonNull(uuid);
		Objects.requireNonNull(address);

		this.uuid = uuid;
		this.address = address;
	}

	public static FetchAtomsCancelAction of(String uuid, RadixAddress address) {
		return new FetchAtomsCancelAction(uuid, address);
	}

	/**
	 * The unique id representing a fetch atoms flow. That is, each type of action in a single flow instance
	 * must have the same unique id.
	 *
	 * @return the id of the flow the action is a part of
	 */
	public String getUuid() {
		return uuid;
	}

	/**
	 * The address on which to query atoms from
	 *
	 * @return the address to query atoms from
	 */
	public RadixAddress getAddress() {
		return address;
	}

	@Override
	public RadixNode getNode() {
		throw new UnsupportedOperationException();
	}

	@Override
	public String toString() {
		return "FETCH_ATOMS_CANCEL " + uuid + " " + address;
	}
}
