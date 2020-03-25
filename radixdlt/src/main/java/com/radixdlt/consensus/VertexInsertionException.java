package com.radixdlt.consensus;

/**
 * An exception indicating a failure in inserting a vertex into a VertexStore
 */
public class VertexInsertionException extends Exception {
	VertexInsertionException(String message) {
		super(message);
	}

	VertexInsertionException(String message, Exception cause) {
		super(message, cause);
	}
}
