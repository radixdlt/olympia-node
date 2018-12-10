package com.radixdlt.client.application.translate;

import com.radixdlt.client.core.atoms.particles.SpunParticle;

/**
 * Java version of redux-like reducer where particles are the actions
 *
 * @param <T> The class of the state to manage
 */
public interface ParticleReducer<T extends ApplicationState> {
	Class<T> stateClass();
	T initialState();
	T reduce(T state, SpunParticle p);
}
