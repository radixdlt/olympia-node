package com.radixdlt.mempool;

/**
 * Exception thrown when an attempt to add new items would
 * exceed the mempool's maximum capacity.
 */
public class MempoolFullException extends Exception {

	public MempoolFullException(String message) {
		super(message);
	}

}
