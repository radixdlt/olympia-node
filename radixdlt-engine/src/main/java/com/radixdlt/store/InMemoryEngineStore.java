/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 */

package com.radixdlt.store;

import com.google.common.primitives.UnsignedBytes;
import com.radixdlt.application.system.state.SystemData;
import com.radixdlt.application.system.state.VirtualParent;
import com.radixdlt.application.validators.state.ValidatorData;
import com.radixdlt.atom.CloseableCursor;
import com.radixdlt.atom.SubstateId;
import com.radixdlt.atom.SubstateTypeId;
import com.radixdlt.atom.Txn;
import com.radixdlt.application.tokens.state.TokenResource;
import com.radixdlt.constraintmachine.SubstateIndex;
import com.radixdlt.constraintmachine.REStateUpdate;
import com.radixdlt.constraintmachine.RawSubstateBytes;
import com.radixdlt.constraintmachine.SystemMapKey;
import com.radixdlt.constraintmachine.exceptions.VirtualParentStateDoesNotExist;
import com.radixdlt.constraintmachine.exceptions.VirtualSubstateAlreadyDownException;
import com.radixdlt.engine.RadixEngineException;
import com.radixdlt.identifiers.REAddr;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

public final class InMemoryEngineStore<M> implements EngineStore<M> {
	private final Object lock = new Object();
	private final Map<SubstateId, REStateUpdate> storedState = new HashMap<>();
	private final Map<REAddr, Supplier<ByteBuffer>> addrParticles = new HashMap<>();
	private final Map<SystemMapKey, RawSubstateBytes> maps = new HashMap<>();

	@Override
	public <R> R transaction(TransactionEngineStoreConsumer<M, R> consumer) throws RadixEngineException {
		return consumer.start(new EngineStoreInTransaction<M>() {
			@Override
			public void storeTxn(Txn txn, List<REStateUpdate> stateUpdates) {
				synchronized (lock) {
					stateUpdates.forEach(i -> storedState.put(i.getId(), i));
					stateUpdates.forEach(update -> {
						// FIXME: Superhack
						if (update.isBootUp()) {
							if (update.getParsed() instanceof TokenResource) {
								var tokenDef = (TokenResource) update.getParsed();
								addrParticles.put(tokenDef.getAddr(), update::getStateBuf);
							} else if (update.getParsed() instanceof VirtualParent) {
								var p = (VirtualParent) update.getParsed();
								var typeByte = p.getData()[0];
								if (typeByte != SubstateTypeId.UNCLAIMED_READDR.id()) {
									var mapKey = SystemMapKey.ofValidatorDataParent(typeByte);
									maps.put(mapKey, update.getRawSubstateBytes());
								}
							} else if (update.getParsed() instanceof ValidatorData) {
								var data = (ValidatorData) update.getParsed();
								var mapKey = SystemMapKey.ofValidatorData(
									update.typeByte(),
									data.getValidatorKey().getCompressedBytes()
								);
								maps.put(mapKey, update.getRawSubstateBytes());
							} else if (update.getParsed() instanceof SystemData) {
								var mapKey = SystemMapKey.ofSystem(update.typeByte());
								maps.put(mapKey, update.getRawSubstateBytes());
							}
						} else if (update.isShutDown()) {
							if (update.getParsed() instanceof ValidatorData) {
								var data = (ValidatorData) update.getParsed();
								var mapKey = SystemMapKey.ofValidatorData(
									update.typeByte(),
									data.getValidatorKey().getCompressedBytes()
								);
								maps.remove(mapKey);
							} else if (update.getParsed() instanceof SystemData) {
								var mapKey = SystemMapKey.ofSystem(update.typeByte());
								maps.remove(mapKey);
							}
						}
					});
				}
			}

			@Override
			public void storeMetadata(M metadata) {

			}

			@Override
			public ByteBuffer verifyVirtualSubstate(SubstateId substateId)
				throws VirtualSubstateAlreadyDownException, VirtualParentStateDoesNotExist {
				synchronized (lock) {
					var parent = substateId.getVirtualParent().orElseThrow();
					var update = storedState.get(parent);
					if (update == null || !(update.getParsed() instanceof VirtualParent)) {
						throw new VirtualParentStateDoesNotExist(parent);
					}

					var inst = storedState.get(substateId);
					if (inst != null && inst.isShutDown()) {
						throw new VirtualSubstateAlreadyDownException(substateId);
					}

					return update.getStateBuf();
				}
			}

			@Override
			public Optional<ByteBuffer> loadSubstate(SubstateId substateId) {
				synchronized (lock) {
					var inst = storedState.get(substateId);
					if (inst == null || !inst.isBootUp()) {
						return Optional.empty();
					}

					return Optional.of(inst.getStateBuf());
				}
			}

			@Override
			public CloseableCursor<RawSubstateBytes> openIndexedCursor(SubstateIndex<?> index) {
				return InMemoryEngineStore.this.openIndexedCursor(index);
			}

			@Override
			public Optional<ByteBuffer> loadResource(REAddr addr) {
				synchronized (lock) {
					var supplier = addrParticles.get(addr);
					return supplier == null ? Optional.empty() : Optional.of(supplier.get());
				}
			}
		});
	}

	@Override
	public CloseableCursor<RawSubstateBytes> openIndexedCursor(SubstateIndex<?> index) {
		final List<RawSubstateBytes> substates = new ArrayList<>();
		synchronized (lock) {
			for (var i : storedState.values()) {
				if (!i.isBootUp()) {
					continue;
				}
				if (!index.test(i.getRawSubstateBytes())) {
					continue;
				}
				substates.add(i.getRawSubstateBytes());
			}
		}
		substates.sort(Comparator.comparing(RawSubstateBytes::getData, UnsignedBytes.lexicographicalComparator().reversed()));

		return CloseableCursor.wrapIterator(substates.iterator());
	}

	@Override
	public Optional<RawSubstateBytes> get(SystemMapKey key) {
		return Optional.ofNullable(maps.get(key));
	}

	public boolean contains(SubstateId substateId) {
		synchronized (lock) {
			var inst = storedState.get(substateId);
			return inst != null;
		}
	}
}
