package com.radixdlt.mempool;

import com.radixdlt.common.Atom;

/**
 * Exception thrown when an attempt to add new items would
 * exceed the mempool's maximum capacity.
 */
public class MempoolFullException extends MempoolRejectedException {
	public MempoolFullException(Atom atom, String message) {
		super(atom, message);
	}
}
