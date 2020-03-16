package com.radixdlt.consensus;

/**
 * An exception indicating a failure in inserting a vertex into a VertexStore
 */
final class VertexInsertionException extends Exception {
	VertexInsertionException(Exception cause) {
		super(cause);
	}
}
