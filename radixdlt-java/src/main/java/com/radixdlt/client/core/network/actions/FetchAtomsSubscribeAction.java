package com.radixdlt.client.core.network.actions;

import com.radixdlt.client.atommodel.accounts.RadixAddress;
import com.radixdlt.client.core.network.RadixNode;
import java.util.Objects;

/**
 * A dispatchable fetch atoms action which represents a fetch atom query submitted to a specific node.
 */
public class FetchAtomsSubscribeAction implements FetchAtomsAction {
	private final String uuid;
	private final RadixAddress address;
	private final RadixNode node;

	private FetchAtomsSubscribeAction(
		String uuid,
		RadixAddress address,
		RadixNode node
	) {
		Objects.requireNonNull(uuid);
		Objects.requireNonNull(address);
		Objects.requireNonNull(node);

		this.uuid = uuid;
		this.address = address;
		this.node = node;
	}

	public static FetchAtomsSubscribeAction of(String uuid, RadixAddress address, RadixNode node) {
		return new FetchAtomsSubscribeAction(uuid, address, node);
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

	/**
	 * The node which contains the address to query from
	 * @return node to send query
	 */
	public RadixNode getNode() {
		return node;
	}

	@Override
	public String toString() {
		return "FETCH_ATOMS_SUBSCRIBE " + uuid + " " + address + " " + node;
	}
}
