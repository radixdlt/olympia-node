package com.radixdlt.store;

import com.radixdlt.atom.CloseableCursor;
import com.radixdlt.atom.SubstateId;
import com.radixdlt.atom.Txn;
import com.radixdlt.constraintmachine.SubstateIndex;
import com.radixdlt.constraintmachine.REStateUpdate;
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.constraintmachine.RawSubstateBytes;
import com.radixdlt.constraintmachine.SubstateDeserialization;
import com.radixdlt.identifiers.REAddr;

import java.nio.ByteBuffer;
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
	public Optional<ByteBuffer> loadSubstate(Transaction txn, SubstateId substateId) {
		if (transientStore.getSpin(substateId).isEmpty()) {
			return base.loadSubstate(txn, substateId);
		}

		return transientStore.loadSubstate(txn, substateId);
	}

	@Override
	public CloseableCursor<RawSubstateBytes> openIndexedCursor(
		Transaction dbTxn,
		SubstateIndex index
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
	public Optional<ByteBuffer> loadAddr(Transaction dbTxn, REAddr addr) {
		return transientStore.loadAddr(dbTxn, addr).or(() -> base.loadAddr(dbTxn, addr));
	}
}
