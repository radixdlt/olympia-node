package com.radixdlt.engine;

import com.radixdlt.atoms.ImmutableAtom;
import com.radixdlt.atoms.SpunParticle;
import com.radixdlt.constraintmachine.CMError;
import java.util.Set;

/**
 * Listener for atom events as they go through the Radix Engine pipeline.
 */
public interface AtomEventListener<T extends CMAtom> {
	default void onCMSuccess(T cmAtom, Object computed) {
	}

	default void onCMError(T cmAtom, Set<CMError> errors) {
	}

	default void onStateStore(T cmAtom, Object computed) {
	}

	default void onStateConflict(T cmAtom, SpunParticle issueParticle, ImmutableAtom conflictingAtom) {
	}

	default void onStateMissingDependency(T cmAtom, SpunParticle issueParticle) {
	}
}
