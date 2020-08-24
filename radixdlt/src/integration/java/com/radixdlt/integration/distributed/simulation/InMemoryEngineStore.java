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

package com.radixdlt.integration.distributed.simulation;

import com.radixdlt.constraintmachine.CMMicroInstruction;
import com.radixdlt.constraintmachine.CMMicroInstruction.CMMicroOp;
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.constraintmachine.Spin;
import com.radixdlt.engine.RadixEngineAtom;
import com.radixdlt.identifiers.AID;
import com.radixdlt.store.EngineStore;
import com.radixdlt.store.SpinStateMachine;
import com.radixdlt.utils.Pair;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public final class InMemoryEngineStore<T extends RadixEngineAtom> implements EngineStore<T> {
	private final Map<Particle, Pair<Spin, T>> storedParticles = new ConcurrentHashMap<>();

	@Override
	public void getAtomContaining(Particle particle, boolean isInput, Consumer<T> callback) {
		callback.accept(storedParticles.get(particle).getSecond());
	}

	@Override
	public void storeAtom(T atom) {
		for (CMMicroInstruction microInstruction : atom.getCMInstruction().getMicroInstructions()) {
			if (microInstruction.getMicroOp() == CMMicroOp.PUSH) {
				storedParticles.put(
					microInstruction.getParticle(),
					Pair.of(
						SpinStateMachine.next(getSpin(microInstruction.getParticle())),
						atom
					)
				);
			}
		}
	}

	@Override
	public void deleteAtom(AID atomId) {
		throw new UnsupportedOperationException("Deleting is not supported by this engine store.");
	}

	@Override
	public Spin getSpin(Particle particle) {
		Pair<Spin, T> stored = storedParticles.get(particle);
		return stored == null ? Spin.NEUTRAL : stored.getFirst();
	}
}
