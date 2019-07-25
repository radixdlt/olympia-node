package com.radixdlt.engine;

import com.radixdlt.atoms.ImmutableAtom;
import com.radixdlt.atoms.SpunParticle;

/**
 * The result from running the RadixEngine State check
 */
public interface StateCheckResult {
	interface StateCheckResultAcceptor {
		default void onSuccess() {
		}
		default void onConflict(SpunParticle issueParticle, ImmutableAtom conflictingAtom) {
		}
		default void onMissingDependency(SpunParticle issueParticle) {
		}
	}

	void accept(StateCheckResultAcceptor acceptor);
}
