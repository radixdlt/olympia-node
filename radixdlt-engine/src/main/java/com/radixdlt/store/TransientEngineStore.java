package com.radixdlt.store;

import com.radixdlt.atom.Atom;
import com.radixdlt.atom.ParticleId;
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.constraintmachine.Spin;

import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;

public class TransientEngineStore<M> implements EngineStore<M> {
	private final EngineStore<M> base;
	private InMemoryEngineStore<M> transientStore = new InMemoryEngineStore<>();

	public TransientEngineStore(EngineStore<M> base) {
		this.base = Objects.requireNonNull(base);
	}

	@Override
	public void storeAtom(Transaction txn, Atom atom) {
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
	public Optional<Particle> loadUpParticle(Transaction txn, ParticleId particleId) {
		if (transientStore.getSpin(particleId) == Spin.NEUTRAL) {
			return base.loadUpParticle(txn, particleId);
		}

		return transientStore.loadUpParticle(txn, particleId);
	}
}
