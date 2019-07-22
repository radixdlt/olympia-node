package com.radixdlt.engine;

import com.google.common.collect.ImmutableMap;
import com.radixdlt.constraintmachine.CMAtom;
import com.radixdlt.constraintmachine.CMError;
import java.util.Set;

/**
 * The result from running the RadixEngine Constaint Machine check
 */
public interface ValidationResult {
	interface ValidationResultAcceptor {
		default void onSuccess(ImmutableMap<String, Object> computed) {
		}
		default void onError(Set<CMError> errors) {
		}
	}

	void accept(ValidationResultAcceptor acceptor);
}
