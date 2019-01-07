package com.radixdlt.client.core.network.actions;

import com.radixdlt.client.atommodel.accounts.RadixAddress;
import com.radixdlt.client.core.atoms.AtomObservation;
import com.radixdlt.client.core.network.RadixNodeAction;
import com.radixdlt.client.core.network.RadixNode;
import java.util.Objects;
import java.util.UUID;

/**
 * An action which is part of a fetch atom flow from node search to atom observations
 */
public class FetchAtomsAction implements RadixNodeAction {

	/**
	 * The type of an action representing which part of the flow it is currently at
	 */
	public enum FetchAtomsActionType {
		FIND_A_NODE,
		SUBSCRIBE,
		RECEIVED_ATOM_OBSERVATION,
		CANCEL,
	}

	private final RadixNode node;
	private final FetchAtomsActionType type;
	private final RadixAddress address;
	private final AtomObservation observation;
	private final String uuid;

	private FetchAtomsAction(String uuid, RadixAddress address, FetchAtomsActionType type, RadixNode node, AtomObservation observation) {
		Objects.requireNonNull(uuid);
		Objects.requireNonNull(address);

		this.uuid = uuid;
		this.address = address;
		this.node = node;
		this.type = type;
		this.observation = observation;
	}

	public static FetchAtomsAction findANode(RadixAddress address) {
		return new FetchAtomsAction(UUID.randomUUID().toString(), address, FetchAtomsActionType.FIND_A_NODE, null, null);
	}

	public static FetchAtomsAction submitQuery(String uuid, RadixAddress address, RadixNode node) {
		return new FetchAtomsAction(uuid, address, FetchAtomsActionType.SUBSCRIBE, node, null);
	}

	public static FetchAtomsAction observed(String uuid, RadixAddress address, RadixNode node, AtomObservation observation) {
		return new FetchAtomsAction(uuid, address, FetchAtomsActionType.RECEIVED_ATOM_OBSERVATION, node, observation);
	}

	public static FetchAtomsAction cancel(String uuid, RadixAddress address) {
		return new FetchAtomsAction(uuid, address, FetchAtomsActionType.CANCEL, null, null);
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
	public RadixNode getNode() {
		return node;
	}

	public FetchAtomsActionType getType() {
		return type;
	}

	@Override
	public String toString() {
		return "FETCH_ATOMS(" + type + ") " + uuid + " " + node;
	}
}
