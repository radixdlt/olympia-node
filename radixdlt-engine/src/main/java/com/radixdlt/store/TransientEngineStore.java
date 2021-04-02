package com.radixdlt.store;

import com.radixdlt.atom.Atom;
import com.radixdlt.atom.Substate;
import com.radixdlt.atom.SubstateId;
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
	public boolean isVirtualDown(Transaction txn, SubstateId substateId) {
		return transientStore.isVirtualDown(txn, substateId)
			|| base.isVirtualDown(txn, substateId);
	}

	@Override
	public Optional<Particle> loadUpParticle(Transaction txn, SubstateId substateId) {
		if (transientStore.getSpin(substateId) == Spin.NEUTRAL) {
			return base.loadUpParticle(txn, substateId);
		}

		return transientStore.loadUpParticle(txn, substateId);
	}

	@Override
	public Iterable<Substate> index(Class<? extends Particle> particleClass) {
		throw new UnsupportedOperationException("Transient store should not require up substates.");
	}
}
