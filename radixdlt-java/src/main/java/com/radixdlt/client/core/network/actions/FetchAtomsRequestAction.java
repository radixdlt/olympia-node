package com.radixdlt.client.core.network.actions;

import com.radixdlt.client.atommodel.accounts.RadixAddress;
import com.radixdlt.client.core.network.RadixNode;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * The initial dispatchable fetch atoms action which signals a node must be found to continue the flow.
 */
public class FetchAtomsRequestAction implements FetchAtomsAction, FindANodeAction {
	private final String uuid;
	private final RadixAddress address;

	private FetchAtomsRequestAction(
		String uuid,
		RadixAddress address
	) {
		Objects.requireNonNull(uuid);
		Objects.requireNonNull(address);

		this.uuid = uuid;
		this.address = address;
	}

	public static FetchAtomsRequestAction newRequest(RadixAddress address) {
		return new FetchAtomsRequestAction(UUID.randomUUID().toString(), address);
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
	public Set<Long> getShards() {
		return Collections.singleton(address.getUID().getShard());
	}

	@Override
	public RadixNode getNode() {
		throw new UnsupportedOperationException();
	}

	@Override
	public String toString() {
		return "FETCH_ATOMS_REQUEST " + uuid + " " + address;
	}
}
