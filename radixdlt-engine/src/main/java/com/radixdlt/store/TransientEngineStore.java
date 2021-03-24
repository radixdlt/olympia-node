package com.radixdlt.store;

import com.google.common.hash.HashCode;
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.constraintmachine.Spin;
import com.radixdlt.engine.RadixEngineAtom;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;

public class TransientEngineStore<T extends RadixEngineAtom, M> implements EngineStore<T, M> {
	private final EngineStore<T, M> base;
	private InMemoryEngineStore<T, M> transientStore = new InMemoryEngineStore<>();

	public TransientEngineStore(EngineStore<T, M> base) {
		this.base = Objects.requireNonNull(base);
	}

	@Override
	public void storeAtom(Transaction txn, T atom) {
		transientStore.storeAtom(txn, atom);
	}

	@Override
	public void storeMetadata(Transaction txn, M metadata) {
		// No-op
	}

	@Override
	public <U extends Particle, V> V reduceUpParticles(Class<U> aClass, V v, BiFunction<V, U, V> biFunction) {
		throw new UnsupportedOperationException("Transient store should not require reduction.");
	}

	@Override
	public Transaction createTransaction() {
		return new Transaction() { };
	}

	@Override
	public Spin getSpin(Transaction txn, Particle particle) {
		Spin transientSpin = transientStore.getSpin(txn, particle);
		if (transientSpin != Spin.NEUTRAL) {
			return transientSpin;
		}

		return base.getSpin(txn, particle);
	}

	@Override
	public Optional<Particle> loadUpParticle(Transaction txn, HashCode particleHash) {
		if (transientStore.getSpin(particleHash) == Spin.NEUTRAL) {
			return base.loadUpParticle(txn, particleHash);
		}

		return transientStore.loadUpParticle(txn, particleHash);
	}
}
