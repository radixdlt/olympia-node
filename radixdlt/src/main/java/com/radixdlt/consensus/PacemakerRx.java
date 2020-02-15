package com.radixdlt.consensus;

import java.util.function.Consumer;

/**
 * Async callbacks from pacemaker timeouts
 * TODO: change to an rx interface
 */
public interface PacemakerRx {
	/**
	 * Throw away callback until rx is implemented
	 */
	void addTimeoutCallback(Consumer<Void> callback);

	void start();
}
