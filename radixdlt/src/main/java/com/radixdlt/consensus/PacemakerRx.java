package com.radixdlt.consensus;

import java.util.function.Consumer;

public interface PacemakerRx {
	// TODO: Change to reactive call
	void addTimeoutCallback(Consumer<Void> callback);
	void start();
}
