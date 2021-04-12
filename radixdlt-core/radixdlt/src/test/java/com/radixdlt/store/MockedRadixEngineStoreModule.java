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

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.radixdlt.DefaultSerialization;
import com.radixdlt.atom.Substate;
import com.radixdlt.atom.SubstateId;
import com.radixdlt.atom.SubstateSerializer;
import com.radixdlt.atom.Txn;
import com.radixdlt.constraintmachine.ConstraintMachine;
import com.radixdlt.constraintmachine.REParsedAction;
import com.radixdlt.constraintmachine.REParsedInstruction;
import com.radixdlt.constraintmachine.REParsedTxn;
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.constraintmachine.REInstruction;
import com.radixdlt.constraintmachine.Spin;
import com.radixdlt.atom.Atom;
import com.radixdlt.serialization.DeserializeException;
import com.radixdlt.serialization.Serialization;
import com.radixdlt.statecomputer.LedgerAndBFTProof;
import com.radixdlt.statecomputer.checkpoint.Genesis;
import com.radixdlt.utils.Ints;

import java.util.ArrayList;
import java.util.List;

public class MockedRadixEngineStoreModule extends AbstractModule {
	@Override
	public void configure() {
		bind(Serialization.class).toInstance(DefaultSerialization.getInstance());
	}

	private List<REParsedInstruction> toParsed(Txn txn, InMemoryEngineStore<LedgerAndBFTProof> store) throws DeserializeException {
		var atom = DefaultSerialization.getInstance().fromDson(txn.getPayload(), Atom.class);
		var rawInstructions = ConstraintMachine.toInstructions(atom.getInstructions());
		var instructions = new ArrayList<REParsedInstruction>();
		for (int i = 0; i < rawInstructions.size(); i++) {
			var instruction = rawInstructions.get(i);

			if (!instruction.isPush()) {
				continue;
			}

			final Particle particle;
			final SubstateId substateId;
			try {
				if (instruction.getMicroOp() == REInstruction.REOp.UP) {
					particle = SubstateSerializer.deserialize(instruction.getData());
					substateId = SubstateId.ofSubstate(atom, i);
				} else if (instruction.getMicroOp() == REInstruction.REOp.VDOWN) {
					particle = SubstateSerializer.deserialize(instruction.getData());
					substateId = SubstateId.ofVirtualSubstate(instruction.getData());
				} else if (instruction.getMicroOp() == REInstruction.REOp.DOWN) {
					substateId = SubstateId.fromBytes(instruction.getData());
					var storedParticle = store.loadUpParticle(null, substateId);
					particle = storedParticle.orElseThrow();
				} else if (instruction.getMicroOp() == REInstruction.REOp.LDOWN) {
					int index = Ints.fromByteArray(instruction.getData());
					var dson = rawInstructions.get(index).getData();
					particle = SubstateSerializer.deserialize(dson);
					substateId = SubstateId.ofSubstate(atom, index);
				} else {
					throw new IllegalStateException();
				}
			} catch (DeserializeException e) {
				throw new IllegalStateException();
			}

			var parsed = REParsedInstruction.of(instruction, Substate.create(particle, substateId));
			instructions.add(parsed);
		}

		return instructions;
	}

	@Provides
	@Singleton
	private EngineStore<LedgerAndBFTProof> engineStore(
		@Genesis List<Txn> genesisTxns
	) {
		var inMemoryEngineStore = new InMemoryEngineStore<LedgerAndBFTProof>();
		for (var genesisTxn : genesisTxns) {
			if (!inMemoryEngineStore.containsTxn(genesisTxn.getId())) {
				try {
					var dbTxn = inMemoryEngineStore.createTransaction();
					var instructions = toParsed(genesisTxn, inMemoryEngineStore);
					inMemoryEngineStore.storeAtom(dbTxn, genesisTxn, instructions);
					dbTxn.commit();
				} catch (DeserializeException e) {
					throw new IllegalStateException();
				}
			}
		}
		return inMemoryEngineStore;
	}
}
