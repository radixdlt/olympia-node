package com.radixdlt.client.core.network.jsonrpc;

import io.reactivex.Observable;

public interface PersistentChannel {
	boolean sendMessage(String s);
	Observable<String> getMessages();
}
