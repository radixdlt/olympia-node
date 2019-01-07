package com.radixdlt.client.core.network.actions;

import com.radixdlt.client.atommodel.accounts.RadixAddress;
import com.radixdlt.client.core.atoms.AtomObservation;
import com.radixdlt.client.core.network.RadixNodeAction;
import com.radixdlt.client.core.network.RadixNode;
import io.reactivex.annotations.Nullable;
import java.util.Objects;
import java.util.UUID;

/**
 * 	 * Creates a dispatchable fetch atoms action which represents an atom observed from a specific node for an atom fetch flow.
 */
public class FetchAtomsObservationAction implements FetchAtomsAction {
	private final String uuid;
	private final RadixAddress address;
	private final RadixNode node;
	private final AtomObservation observation;

	private FetchAtomsObservationAction(
		String uuid,
		RadixAddress address,
		RadixNode node,
		AtomObservation observation
	) {
		Objects.requireNonNull(uuid);
		Objects.requireNonNull(address);
		Objects.requireNonNull(node);
		Objects.requireNonNull(observation);

		this.uuid = uuid;
		this.address = address;
		this.node = node;
		this.observation = observation;
	}

	public static FetchAtomsObservationAction of(String uuid, RadixAddress address, RadixNode node, AtomObservation observation) {
		return new FetchAtomsObservationAction(uuid, address, node, observation);
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

	/**
	 * The atom observation associated with this action. Should only be called when type == RECEIVED_ATOM_OBSERVATION.
	 *
	 * @return the atom observation of the action
	 */
	public AtomObservation getObservation() {
		return observation;
	}

	@Override
	public String toString() {
		return "FETCH_ATOMS_OBSERVATION " + uuid + " " + address + " " + node;
	}
}
