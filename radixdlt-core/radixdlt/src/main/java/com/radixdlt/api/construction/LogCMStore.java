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
import com.radixdlt.atomos.RriId;
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.constraintmachine.REInstruction;
import com.radixdlt.store.AtomIndex;
import com.radixdlt.store.CMStore;
import com.radixdlt.store.ImmutableIndex;
import com.radixdlt.utils.functional.Result;

import java.nio.ByteBuffer;
import java.util.Optional;

import static com.radixdlt.serialization.SerializationUtils.restore;

/**
 * CMStore which allows one to parse transactions given
 * the append log as the backend store.
 */
public final class LogCMStore implements CMStore {
	private final AtomIndex atomIndex;
	private final ImmutableIndex immutableIndex;

	@Inject
	public LogCMStore(
		AtomIndex atomIndex,
		ImmutableIndex immutableIndex
	) {
		this.atomIndex = atomIndex;
		this.immutableIndex = immutableIndex;
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
	public Optional<Particle> loadRriId(Transaction tx, RriId rriId) {
		return immutableIndex.loadRriId(tx, rriId);
	}

	@Override
	public Optional<Particle> loadUpParticle(Transaction dbTxn, SubstateId substateId) {
		var txnId = substateId.getTxnId();
		return atomIndex.get(txnId)
			.flatMap(txn ->
				restore(DefaultSerialization.getInstance(), txn.getPayload(), Atom.class)
					.flatMap(a -> {
						var buf = ByteBuffer.wrap(a.getUnsignedBlob());
						var index = substateId.getIndex().orElseThrow();
						int cur = 0;
						while (buf.hasRemaining()) {
							try {
								var i = REInstruction.readFrom(buf);
								if (cur == index) {
									Particle p = i.getData();
									return Result.ok(p);
								}
							} catch (Exception e) {
								return Result.fail(e.getMessage());
							}
							cur++;
						}

						return Result.fail("Not found.");
					})
					.toOptional()
			);
	}
}
