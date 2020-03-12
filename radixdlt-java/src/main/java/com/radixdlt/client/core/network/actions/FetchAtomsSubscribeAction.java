package com.radixdlt.client.core.network.actions;

import com.radixdlt.identifiers.RadixAddress;
import com.radixdlt.client.core.network.RadixNode;
import java.util.Objects;

/**
 * A dispatchable fetch atoms action which represents a fetch atom query submitted to a specific node.
 */
public final class FetchAtomsSubscribeAction implements FetchAtomsAction {
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

	@Override
	public String getUuid() {
		return uuid;
	}

	@Override
	public RadixAddress getAddress() {
		return address;
	}

	@Override
	public RadixNode getNode() {
		return node;
	}

	@Override
	public String toString() {
		return "FETCH_ATOMS_SUBSCRIBE " + uuid + " " + address + " " + node;
	}
}
