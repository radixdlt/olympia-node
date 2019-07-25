package com.radixdlt.engine;

import com.google.common.collect.ImmutableMap;
import com.radixdlt.constraintmachine.CMAtom;
import com.radixdlt.constraintmachine.CMError;
import java.util.Set;

/**
 * The result from running the RadixEngine Constraint Machine check
 */
public interface ValidationResult {
	interface ValidationResultAcceptor {
		default void onSuccess(CMAtom cmAtom, ImmutableMap<String, Object> computed) {
		}
		default void onError(CMAtom cmAtom, Set<CMError> errors) {
		}
	}

	void accept(ValidationResultAcceptor acceptor);
}
