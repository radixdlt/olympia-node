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

import com.radixdlt.atom.Substate;
import com.radixdlt.atom.CloseableCursor;
import com.radixdlt.atom.SubstateId;
import com.radixdlt.atom.SubstateStore;
import com.radixdlt.atom.Txn;
import com.radixdlt.atommodel.system.state.EpochData;
import com.radixdlt.atommodel.system.state.SystemParticle;
import com.radixdlt.atommodel.tokens.state.TokenResource;
import com.radixdlt.constraintmachine.DownAllIndex;
import com.radixdlt.constraintmachine.REStateUpdate;
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.constraintmachine.REOp;
import com.radixdlt.constraintmachine.RawSubstateBytes;
import com.radixdlt.constraintmachine.SubstateDeserialization;
import com.radixdlt.identifiers.REAddr;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;

public final class InMemoryEngineStore<M> implements EngineStore<M>, SubstateStore {
	private final Object lock = new Object();
	private final Map<SubstateId, REStateUpdate> storedParticles = new HashMap<>();
	private final Map<REAddr, Particle> addrParticles = new HashMap<>();

	@Override
	public void storeTxn(Transaction dbTxn, Txn txn, List<REStateUpdate> stateUpdates) {
		synchronized (lock) {
			stateUpdates.forEach(i -> storedParticles.put(i.getSubstate().getId(), i));
			stateUpdates.stream()
				.filter(REStateUpdate::isBootUp)
				.map(REStateUpdate::getRawSubstate)
				.forEach(p -> {
					// FIXME: Superhack
					if (p instanceof TokenResource) {
						var tokenDef = (TokenResource) p;
						addrParticles.put(tokenDef.getAddr(), p);
					} else if (p instanceof SystemParticle) {
						addrParticles.put(REAddr.ofSystem(), p);
					} else if (p instanceof EpochData) {
						addrParticles.put(REAddr.ofSystem(), p);
					}
				});
		}
	}

	@Override
	public void storeMetadata(Transaction txn, M metadata) {
		 // No-op
	}

	@Override
	public <V> V reduceUpParticles(
		Class<? extends Particle> particleClass,
		V initial,
		BiFunction<V, Particle, V> outputReducer,
		SubstateDeserialization substateDeserialization
	) {
		V v = initial;
		synchronized (lock) {
			for (var i : storedParticles.values()) {
				if (!i.isBootUp() || !particleClass.isInstance(i.getRawSubstate())) {
					continue;
				}
				v = outputReducer.apply(v, particleClass.cast(i.getRawSubstate()));
			}
		}
		return v;
	}

	@Override
	public CloseableCursor<RawSubstateBytes> openIndexedCursor(Transaction dbTxn, DownAllIndex index) {
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

		return CloseableCursor.wrapIterator(substates.iterator());
	}

	@Override
	public CloseableCursor<Substate> openIndexedCursor(Class<? extends Particle> substateClass, SubstateDeserialization deserialization) {
		final List<Substate> substates = new ArrayList<>();
		synchronized (lock) {
			for (var i : storedParticles.values()) {
				if (!i.isBootUp() || !substateClass.isInstance(i.getRawSubstate())) {
					continue;
				}
				substates.add(i.getSubstate());
			}
		}

		return CloseableCursor.wrapIterator(substates.iterator());
	}

	@Override
	public Transaction createTransaction() {
		return new Transaction() { };
	}

	@Override
	public boolean isVirtualDown(Transaction txn, SubstateId substateId) {
		synchronized (lock) {
			var inst = storedParticles.get(substateId);
			return inst != null && inst.isShutDown();
		}
	}

	public Optional<REOp> getSpin(SubstateId substateId) {
		synchronized (lock) {
			var inst = storedParticles.get(substateId);
			return Optional.ofNullable(inst).map(REStateUpdate::getOp);
		}
	}

	@Override
	public Optional<Particle> loadUpParticle(Transaction txn, SubstateId substateId, SubstateDeserialization deserialization) {
		synchronized (lock) {
			var inst = storedParticles.get(substateId);
			if (inst == null || inst.getOp() != REOp.UP) {
				return Optional.empty();
			}

			var particle = inst.getRawSubstate();
			return Optional.of(particle);
		}
	}

	@Override
	public Optional<Particle> loadAddr(Transaction dbTxn, REAddr rri, SubstateDeserialization deserialization) {
		synchronized (lock) {
			return Optional.ofNullable(addrParticles.get(rri));
		}
	}
}
