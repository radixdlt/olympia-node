package com.radixdlt.client.core.network.actions;

import com.radixdlt.client.core.network.RadixNodeAction;

public interface JsonRpcAction extends RadixNodeAction {
	enum JsonRpcActionType {
		REQUEST,
		RESULT,
		ERROR,
	}

	JsonRpcActionType getJsonRpcActionType();
}
