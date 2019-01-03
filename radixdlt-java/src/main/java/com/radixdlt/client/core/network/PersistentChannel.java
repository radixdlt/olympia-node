package com.radixdlt.client.core.network;

import io.reactivex.Observable;

public interface PersistentChannel {
	boolean sendMessage(String s);
	Observable<String> getMessages();
}
