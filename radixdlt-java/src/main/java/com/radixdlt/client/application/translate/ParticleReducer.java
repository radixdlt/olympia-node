package com.radixdlt.client.application.translate;

import com.radixdlt.client.core.atoms.particles.Particle;

/**
 * Temporary class for reducing up particles into an application state.
 * TODO: Introduce the AtomOS layer in between Application and Particles
 * TODO: which will reduce fungible, indexable, and single state particle types
 * TODO: into a state machine upon which application state can be built on
 * TODO: top of
 *
 * @param <T> The class of the state to manage
 */
public interface ParticleReducer<T extends ApplicationState> {
	Class<T> stateClass();
	T initialState();
	T reduce(T state, Particle p);
	T combine(T state0, T state1);
}
