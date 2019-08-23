package com.radixdlt.engine;

import com.radixdlt.atoms.ImmutableAtom;
import com.radixdlt.atoms.SpunParticle;
import com.radixdlt.constraintmachine.CMError;
import java.util.Set;

/**
 * Listener for atom events as they go through the Radix Engine pipeline.
 */
public interface AtomEventListener {
	default void onCMSuccess(CMAtom cmAtom, Object computed) {
	}

	default void onCMError(CMAtom cmAtom, Set<CMError> errors) {
	}

	default void onStateStore(CMAtom cmAtom, Object computed) {
	}

	default void onStateConflict(CMAtom cmAtom, SpunParticle issueParticle, ImmutableAtom conflictingAtom) {
	}

	default void onStateMissingDependency(CMAtom cmAtom, SpunParticle issueParticle) {
	}
}
