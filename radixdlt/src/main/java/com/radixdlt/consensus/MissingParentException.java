package com.radixdlt.consensus;

import java.util.Objects;

/**
 * Exception specifying that a vertex cannot be inserted because
 * it's parent is missing from the current store.
 */
class MissingParentException extends VertexInsertionException {
	MissingParentException(Integer parentId) {
		super("Parent Vertex missing: " + Objects.requireNonNull(parentId));
	}
}
