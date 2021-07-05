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
import com.radixdlt.atom.CloseableCursor;
import com.radixdlt.atom.SubstateId;
import com.radixdlt.atom.Txn;
import com.radixdlt.application.tokens.state.TokenResource;
import com.radixdlt.constraintmachine.SubstateIndex;
import com.radixdlt.constraintmachine.REStateUpdate;
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.constraintmachine.RawSubstateBytes;
import com.radixdlt.constraintmachine.SubstateDeserialization;
import com.radixdlt.engine.RadixEngineException;
import com.radixdlt.identifiers.REAddr;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Supplier;

public final class InMemoryEngineStore<M> implements EngineStore<M> {
	private final Object lock = new Object();
	private final Map<SubstateId, REStateUpdate> storedParticles = new HashMap<>();
	private final Map<REAddr, Supplier<ByteBuffer>> addrParticles = new HashMap<>();

	@Override
	public <R> R transaction(TransactionEngineStoreConsumer<M, R> consumer) throws RadixEngineException {
		return consumer.start(new EngineStoreInTransaction<M>() {
			@Override
			public void storeTxn(Txn txn, List<REStateUpdate> stateUpdates) {
				synchronized (lock) {
					stateUpdates.forEach(i -> storedParticles.put(i.getId(), i));
					stateUpdates.stream()
						.filter(REStateUpdate::isBootUp)
						.forEach(p -> {
							// FIXME: Superhack
							if (p.getParsed() instanceof TokenResource) {
								var tokenDef = (TokenResource) p.getParsed();
								addrParticles.put(tokenDef.getAddr(), p::getStateBuf);
							}
						});
				}
			}

			@Override
			public void storeMetadata(M metadata) {

			}

			@Override
			public boolean isVirtualDown(SubstateId substateId) {
				synchronized (lock) {
					var inst = storedParticles.get(substateId);
					return inst != null && inst.isShutDown();
				}
			}

			@Override
			public Optional<ByteBuffer> loadSubstate(SubstateId substateId) {
				synchronized (lock) {
					var inst = storedParticles.get(substateId);
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
			for (var i : storedParticles.values()) {
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

	public boolean contains(SubstateId substateId) {
		synchronized (lock) {
			var inst = storedParticles.get(substateId);
			return inst != null;
		}
	}

	@Override
	public <V> V reduceUpParticles(
		V initial,
		BiFunction<V, Particle, V> outputReducer,
		SubstateDeserialization substateDeserialization,
		Class<? extends Particle>... particleClass
	) {
		V v = initial;
		var types = Set.of(particleClass);

		synchronized (lock) {
			for (var i : storedParticles.values()) {
				if (!i.isBootUp() || !isOneOf(types, i.getParsed())) {
					continue;
				}

				v = outputReducer.apply(v, (Particle) i.getParsed());
			}
		}
		return v;
	}

	private static boolean isOneOf(Set<Class<? extends Particle>> bundle, Object instance) {
		return bundle.stream().anyMatch(v -> v.isInstance(instance));
	}

}
