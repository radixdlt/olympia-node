package com.radixdlt.consensus;

/**
 * An exception indicating a failure in inserting a vertex into a VertexStore
 */
class VertexInsertionException extends Exception {
	VertexInsertionException(String message) {
		super(message);
	}

	VertexInsertionException(Exception cause) {
		super(cause);
	}
}
