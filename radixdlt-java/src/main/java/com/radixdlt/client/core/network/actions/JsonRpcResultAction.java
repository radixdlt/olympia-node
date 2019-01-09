package com.radixdlt.client.core.network.actions;

import com.radixdlt.client.core.network.RadixNodeAction;

/**
 * The result from executing a Json Rpc Method
 * @param <T> class of result
 */
public interface JsonRpcResultAction<T> extends RadixNodeAction {
	T getResult();
}
