package com.radixdlt.store;

import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.constraintmachine.Spin;
import com.radixdlt.engine.RadixEngineAtom;
import java.util.Objects;
import java.util.function.BiFunction;

public class TransientEngineStore<T extends RadixEngineAtom> implements EngineStore<T> {
	private final EngineStore<T> base;
	private InMemoryEngineStore<T> transientStore = new InMemoryEngineStore<>();

	public TransientEngineStore(EngineStore<T> base) {
		this.base = Objects.requireNonNull(base);
	}

	@Override
	public void storeAtom(T atom) {
		transientStore.storeAtom(atom);
	}

	@Override
	public <U extends Particle, V> V compute(Class<U> aClass, V v, BiFunction<V, U, V> biFunction, BiFunction<V, U, V> biFunction1) {
		V baseResult = base.compute(aClass, v, biFunction, biFunction1);
		return transientStore.compute(aClass, baseResult, biFunction, biFunction1);
	}

	@Override
	public Spin getSpin(Particle particle) {
		Spin transientSpin = transientStore.getSpin(particle);
		if (transientSpin != Spin.NEUTRAL) {
			return transientSpin;
		}

		return base.getSpin(particle);
	}
}
