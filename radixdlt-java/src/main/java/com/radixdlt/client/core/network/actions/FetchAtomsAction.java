package com.radixdlt.client.core.network.actions;

import com.radixdlt.client.atommodel.accounts.RadixAddress;
import com.radixdlt.client.core.network.RadixNodeAction;

/**
 * A dispatchable fetch atoms action which represents an atom observed from a specific node for an atom fetch flow.
 */
public interface FetchAtomsAction extends RadixNodeAction {
	/**
	 * The unique id representing a fetch atoms flow. That is, each type of action in a single flow instance
	 * must have the same unique id.
	 *
	 * @return the id of the flow the action is a part of
	 */
	String getUuid();

	/**
	 * The address on which to query atoms from
	 *
	 * @return the address to query atoms from
	 */
	RadixAddress getAddress();
}
