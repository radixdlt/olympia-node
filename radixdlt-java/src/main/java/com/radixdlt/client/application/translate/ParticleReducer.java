package com.radixdlt.client.application.translate;

import com.radixdlt.client.core.atoms.particles.Particle;

/**
 * Java version of redux-like reducer where particles are the actions
 *
 * @param <T> The class of the state to manage
 */
public interface ParticleReducer<T> {
	T initialState();
	T reduce(T state, Particle p);
}
