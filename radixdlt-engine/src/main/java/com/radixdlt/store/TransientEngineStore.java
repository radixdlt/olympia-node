package com.radixdlt.store;

import com.radixdlt.atom.CloseableCursor;
import com.radixdlt.atom.Substate;
import com.radixdlt.atom.SubstateId;
import com.radixdlt.atom.Txn;
import com.radixdlt.constraintmachine.ShutdownAllIndex;
import com.radixdlt.constraintmachine.REStateUpdate;
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.constraintmachine.RawSubstateBytes;
import com.radixdlt.constraintmachine.SubstateDeserialization;
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
	public void storeTxn(Transaction dbTxn, Txn txn, List<REStateUpdate> stateUpdates) {
		transientStore.storeTxn(dbTxn, txn, stateUpdates);
	}

	@Override
	public void storeMetadata(Transaction txn, M metadata) {
		// No-op
	}

	@Override
	public <V> V reduceUpParticles(
		V v,
		BiFunction<V, Particle, V> biFunction,
		SubstateDeserialization substateDeserialization,
		Class<? extends Particle>... aClass
	) {
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
	public Optional<Particle> loadUpParticle(Transaction txn, SubstateId substateId, SubstateDeserialization deserialization) {
		if (transientStore.getSpin(substateId).isEmpty()) {
			return base.loadUpParticle(txn, substateId, deserialization);
		}

		return transientStore.loadUpParticle(txn, substateId, deserialization);
	}

	@Override
	public CloseableCursor<RawSubstateBytes> openIndexedCursor(
		Transaction dbTxn,
		ShutdownAllIndex index
	) {
		return CloseableCursor.concat(
			transientStore.openIndexedCursor(dbTxn, index),
			() -> CloseableCursor.filter(
				base.openIndexedCursor(dbTxn, index),
				s -> transientStore.getSpin(SubstateId.fromBytes(s.getId())).isEmpty()
			)
		);
	}

	@Override
	public CloseableCursor<Substate> openIndexedCursor(
		Class<? extends Particle> particleClass,
		SubstateDeserialization deserialization
	) {
		return CloseableCursor.concat(
			transientStore.openIndexedCursor(particleClass, deserialization),
			() -> CloseableCursor.filter(
				base.openIndexedCursor(particleClass, deserialization),
				s -> transientStore.getSpin(s.getId()).isEmpty()
			)
		);
	}

	@Override
	public Optional<Particle> loadAddr(Transaction dbTxn, REAddr rri, SubstateDeserialization deserialization) {
		return transientStore.loadAddr(dbTxn, rri, deserialization)
			.or(() -> base.loadAddr(dbTxn, rri, deserialization));
	}
}
