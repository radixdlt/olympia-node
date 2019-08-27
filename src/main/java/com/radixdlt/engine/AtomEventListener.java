package com.radixdlt.engine;

import com.radixdlt.constraintmachine.DataPointer;
import com.radixdlt.constraintmachine.CMError;
import java.util.Set;

/**
 * Listener for atom events as they go through the Radix Engine pipeline.
 */
public interface AtomEventListener<T extends RadixEngineAtom> {
	default void onCMSuccess(T cmAtom) {
	}

	default void onCMError(T cmAtom, Set<CMError> errors) {
	}

	default void onStateStore(T cmAtom) {
	}

	default void onStateConflict(T cmAtom, DataPointer issueParticle, T conflictingAtom) {
	}

	default void onStateMissingDependency(T cmAtom, DataPointer issueParticle) {
	}
}
