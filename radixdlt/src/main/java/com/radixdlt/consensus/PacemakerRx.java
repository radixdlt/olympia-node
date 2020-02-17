package com.radixdlt.consensus;

import java.util.function.LongConsumer;

/**
 * Async callbacks from pacemaker timeouts
 * TODO: change to an rx interface
 */
public interface PacemakerRx {
	/**
	 * Throw away callback until rx is implemented
	 */
	void addTimeoutCallback(LongConsumer callback);

	void start();
}
