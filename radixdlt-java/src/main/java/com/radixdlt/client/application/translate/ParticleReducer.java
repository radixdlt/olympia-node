package com.radixdlt.client.application.translate;

import com.radixdlt.client.core.atoms.particles.Particle;

public interface ParticleReducer<T> {
	T initialState();
	T reduce(T state, Particle p);
}
