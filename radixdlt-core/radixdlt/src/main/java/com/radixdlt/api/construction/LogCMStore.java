/*
 * (C) Copyright 2021 Radix DLT Ltd
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
 *
 */

package com.radixdlt.api.construction;

import com.google.inject.Inject;
import com.radixdlt.DefaultSerialization;
import com.radixdlt.atom.Atom;
import com.radixdlt.atom.SubstateId;
import com.radixdlt.atom.SubstateSerializer;
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.constraintmachine.REInstruction;
import com.radixdlt.store.AtomIndex;
import com.radixdlt.store.CMStore;

import java.util.Optional;
import java.util.stream.Collectors;

import static com.radixdlt.serialization.SerializationUtils.restore;

/**
 * CMStore which allows one to parse transactions given
 * the append log as the backend store.
 */
public final class LogCMStore implements CMStore {
	private final AtomIndex atomIndex;

	@Inject
	public LogCMStore(AtomIndex atomIndex) {
		this.atomIndex = atomIndex;
	}

	@Override
	public Transaction createTransaction() {
		return null;
	}

	@Override
	public boolean isVirtualDown(Transaction dbTxn, SubstateId substateId) {
		return false;
	}

	@Override
	public Optional<Particle> loadUpParticle(Transaction dbTxn, SubstateId substateId) {
		var txnId = substateId.getTxnId();
		return atomIndex.get(txnId)
			.flatMap(txn ->
				restore(DefaultSerialization.getInstance(), txn.getPayload(), Atom.class)
					.map(a -> a.getInstructions().stream().map(REInstruction::create)
						.collect(Collectors.toList()))
					.map(i -> i.get(substateId.getIndex().orElseThrow()))
					.flatMap(i -> SubstateSerializer.deserializeToResult(i.getData()))
					.toOptional()
			);
	}
}
