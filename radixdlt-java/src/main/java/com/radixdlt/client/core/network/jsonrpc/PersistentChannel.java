package com.radixdlt.client.core.network.jsonrpc;

import io.reactivex.functions.Cancellable;
import java.util.function.Consumer;

public interface PersistentChannel {
	boolean sendMessage(String s);

	Cancellable addListener(Consumer<String> messageListener);
}
