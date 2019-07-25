package com.radixdlt.engine;

import com.google.common.collect.ImmutableMap;
import com.radixdlt.atoms.ImmutableAtom;
import com.radixdlt.atoms.SpunParticle;
import com.radixdlt.constraintmachine.CMAtom;

/**
 * The result from running the RadixEngine State check
 */
public interface StateCheckResult {
	interface StateCheckResultAcceptor {
		default void onSuccess(CMAtom cmAtom, ImmutableMap<String, Object> computed) {
		}
		default void onConflict(CMAtom cmAtom, SpunParticle issueParticle, ImmutableAtom conflictingAtom) {
		}
		default void onMissingDependency(CMAtom cmAtom, SpunParticle issueParticle) {
		}
	}

	void accept(StateCheckResultAcceptor acceptor);
}
