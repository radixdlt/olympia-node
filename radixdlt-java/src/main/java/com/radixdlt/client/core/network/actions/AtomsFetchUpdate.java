package com.radixdlt.client.core.network.actions;

import com.radixdlt.client.atommodel.accounts.RadixAddress;
import com.radixdlt.client.core.atoms.AtomObservation;
import com.radixdlt.client.core.network.RadixNodeAction;
import com.radixdlt.client.core.network.RadixPeer;
import java.util.Objects;
import java.util.UUID;

public class AtomsFetchUpdate implements RadixNodeAction {
	public enum AtomsFetchState {
		SEARCHING_FOR_NODE,
		SUBMITTING,
		ON_CANCEL,
		ATOM_OBSERVATION,
	}

	private final RadixPeer node;
	private final AtomsFetchState state;
	private final RadixAddress address;
	private final AtomObservation observation;
	private final String uuid;

	public AtomsFetchUpdate(String uuid, RadixAddress address, AtomsFetchState state, RadixPeer node, AtomObservation observation) {
		Objects.requireNonNull(uuid);
		Objects.requireNonNull(address);

		this.uuid = uuid;
		this.address = address;
		this.node = node;
		this.state = state;
		this.observation = observation;
	}

	public static AtomsFetchUpdate searchForNode(RadixAddress address) {
		return new AtomsFetchUpdate(UUID.randomUUID().toString(), address, AtomsFetchState.SEARCHING_FOR_NODE, null, null);
	}

	public static AtomsFetchUpdate submitQuery(String uuid, RadixAddress address, RadixPeer node) {
		return new AtomsFetchUpdate(uuid, address, AtomsFetchState.SUBMITTING, node, null);
	}

	public static AtomsFetchUpdate observed(String uuid, RadixAddress address, RadixPeer node, AtomObservation observation) {
		return new AtomsFetchUpdate(uuid, address, AtomsFetchState.ATOM_OBSERVATION, node, observation);
	}

	public static AtomsFetchUpdate cancel(String uuid, RadixAddress address) {
		return new AtomsFetchUpdate(uuid, address, AtomsFetchState.ON_CANCEL, null, null);
	}

	public AtomObservation getObservation() {
		return observation;
	}

	public String getUuid() {
		return uuid;
	}

	public RadixAddress getAddress() {
		return address;
	}

	@Override
	public RadixPeer getNode() {
		return node;
	}

	public AtomsFetchState getState() {
		return state;
	}
}
