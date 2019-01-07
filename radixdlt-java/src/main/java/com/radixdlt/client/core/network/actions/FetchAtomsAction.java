package com.radixdlt.client.core.network.actions;

import com.radixdlt.client.atommodel.accounts.RadixAddress;
import com.radixdlt.client.core.network.RadixNodeAction;

/**
 * A dispatchable fetch atoms action which represents an atom observed from a specific node for an atom fetch flow.
 */
public interface FetchAtomsAction extends RadixNodeAction {
	String getUuid();
	RadixAddress getAddress();
}
