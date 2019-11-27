package com.radixdlt.engine;

import com.radixdlt.common.AID;
import com.radixdlt.common.Atom;
import com.radixdlt.constraintmachine.DataPointer;
import com.radixdlt.constraintmachine.CMError;
import com.radixdlt.constraintmachine.Particle;

/**
 * Listener for atom events as they go through the Radix Engine pipeline.
 */
public interface AtomEventListener {
	default void onCMSuccess(Atom atom) {
	}

	default void onCMError(Atom atom, CMError error) {
	}

	default void onStateStore(Atom atom) {
	}

	default void onVirtualStateConflict(Atom atom, DataPointer issueParticle) {
	}

	default void onStateConflict(Atom atom, DataPointer issueParticle, Atom conflictingAtom) {
	}

	default void onStateMissingDependency(AID atomId, Particle particle) {
	}
}
