package com.radixdlt.store;

import com.radixdlt.atom.SubstateCursor;
import com.radixdlt.atom.SubstateId;
import com.radixdlt.atom.Txn;
import com.radixdlt.constraintmachine.REParsedInstruction;
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.constraintmachine.Spin;
import com.radixdlt.identifiers.REAddr;

import java.util.List;
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
	public void storeAtom(Transaction dbTxn, Txn txn, List<REParsedInstruction> stateUpdates) {
		transientStore.storeAtom(dbTxn, txn, stateUpdates);
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
	public SubstateCursor openIndexedCursor(Class<? extends Particle> particleClass) {
		return SubstateCursor.concat(
			transientStore.openIndexedCursor(particleClass),
			() -> SubstateCursor.filter(
				base.openIndexedCursor(particleClass),
				s -> transientStore.getSpin(s.getId()) != Spin.DOWN
			)
		);
	}

	@Override
	public Optional<Particle> loadRri(Transaction dbTxn, REAddr rri) {
		return transientStore.loadRri(dbTxn, rri)
			.or(() -> base.loadRri(dbTxn, rri));
	}
}
