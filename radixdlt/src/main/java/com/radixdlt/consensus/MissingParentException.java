package com.radixdlt.consensus;

import com.radixdlt.crypto.Hash;
import java.util.Objects;

/**
 * Exception specifying that a vertex cannot be inserted because
 * it's parent is missing from the current store.
 */
class MissingParentException extends VertexInsertionException {
	MissingParentException(Hash parentId) {
		super("Parent Vertex missing: " + Objects.requireNonNull(parentId));
	}
}
