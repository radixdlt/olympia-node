package com.radixdlt.mempool;

/**
 * Exception thrown when an attempt to add new items would
 * exceed the mempool's maximum capacity.
 */
public class MempoolDuplicateException extends Exception {

	public MempoolDuplicateException(String message) {
		super(message);
	}

}
