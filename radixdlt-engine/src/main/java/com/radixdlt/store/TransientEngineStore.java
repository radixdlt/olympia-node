package com.radixdlt.store;

import com.radixdlt.atom.CloseableCursor;
import com.radixdlt.atom.SubstateId;
import com.radixdlt.constraintmachine.REProcessedTxn;
import com.radixdlt.constraintmachine.SubstateIndex;
import com.radixdlt.constraintmachine.RawSubstateBytes;
import com.radixdlt.constraintmachine.SystemMapKey;
import com.radixdlt.constraintmachine.exceptions.VirtualParentStateDoesNotExist;
import com.radixdlt.constraintmachine.exceptions.VirtualSubstateAlreadyDownException;
import com.radixdlt.engine.RadixEngineException;
import com.radixdlt.identifiers.REAddr;

import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.Optional;

public class TransientEngineStore<M> implements EngineStore<M> {
	private final EngineStore<M> base;
	private final InMemoryEngineStore<M> transientStore = new InMemoryEngineStore<>();

	public TransientEngineStore(EngineStore<M> base) {
		this.base = Objects.requireNonNull(base);
	}

	@Override
	public <R> R transaction(TransactionEngineStoreConsumer<M, R> consumer) throws RadixEngineException {
		return base.transaction(baseStore ->
			transientStore.transaction(tStore ->
				consumer.start(new EngineStoreInTransaction<M>() {
					@Override
					public void storeTxn(REProcessedTxn txn) {
						tStore.storeTxn(txn);
					}

					@Override
					public void storeMetadata(M metadata) {
						// no-op
					}

					@Override
					public ByteBuffer verifyVirtualSubstate(SubstateId substateId)
						throws VirtualSubstateAlreadyDownException, VirtualParentStateDoesNotExist {
						try {
							return tStore.verifyVirtualSubstate(substateId);
						} catch (VirtualParentStateDoesNotExist e) {
							return baseStore.verifyVirtualSubstate(substateId);
						}
					}

					@Override
					public Optional<ByteBuffer> loadSubstate(SubstateId substateId) {
						if (!transientStore.contains(substateId)) {
							return baseStore.loadSubstate(substateId);
						}

						return tStore.loadSubstate(substateId);
					}

					@Override
					public CloseableCursor<RawSubstateBytes> openIndexedCursor(SubstateIndex<?> index) {
						return tStore.openIndexedCursor(index)
							.concat(() -> baseStore.openIndexedCursor(index)
								.filter(s -> !transientStore.contains(SubstateId.fromBytes(s.getId()))));
					}

					@Override
					public Optional<ByteBuffer> loadResource(REAddr addr) {
						return tStore.loadResource(addr).or(() -> baseStore.loadResource(addr));
					}
				})
			)
		);
	}

	@Override
	public CloseableCursor<RawSubstateBytes> openIndexedCursor(SubstateIndex<?> index) {
		return transientStore.openIndexedCursor(index)
			.concat(() -> base.openIndexedCursor(index)
				.filter(s -> !transientStore.contains(SubstateId.fromBytes(s.getId()))));
	}

	@Override
	public Optional<RawSubstateBytes> get(SystemMapKey key) {
		return transientStore.get(key).or(() -> base.get(key));
	}
}
