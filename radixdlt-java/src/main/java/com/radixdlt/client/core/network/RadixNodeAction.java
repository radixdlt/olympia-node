package com.radixdlt.client.core.network;

/**
 * An action utilized in the Radix Network epics and reducers.
 */
public interface RadixNodeAction {

	/**
	 * The node associated with the network action
	 * @return a radix node
	 */
	RadixNode getNode();
}
