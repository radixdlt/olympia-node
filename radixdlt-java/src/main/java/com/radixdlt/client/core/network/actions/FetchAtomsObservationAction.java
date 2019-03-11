package com.radixdlt.client.core.network.actions;

import com.radixdlt.client.atommodel.accounts.RadixAddress;
import com.radixdlt.client.core.ledger.AtomObservation;
import com.radixdlt.client.core.network.RadixNode;
import java.util.Objects;

/**
 * A dispatchable fetch atoms action which represents an atom observed event from a specific node for an atom fetch flow.
 */
public final class FetchAtomsObservationAction implements FetchAtomsAction {
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

	/**
	 * The atom observation associated with this action.
	 *
	 * @return the atom observation of the action
	 */
	public AtomObservation getObservation() {
		return observation;
	}

	@Override
	public String toString() {
		return "FETCH_ATOMS_OBSERVATION " + node + " " + uuid + " " + observation;
	}
}
