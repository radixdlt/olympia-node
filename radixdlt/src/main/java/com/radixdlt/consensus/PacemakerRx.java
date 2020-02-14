package com.radixdlt.consensus;

import java.util.function.Consumer;

/**
 * Async callbacks from pacemaker timeouts
 * TODO: change to an rx interface
 */
public interface PacemakerRx {
	void addTimeoutCallback(Consumer<Void> callback);
	void start();
}
