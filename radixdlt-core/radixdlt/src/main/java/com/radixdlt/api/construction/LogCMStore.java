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
import com.radixdlt.atom.Substate;
import com.radixdlt.atom.SubstateCursor;
import com.radixdlt.atom.SubstateId;
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.constraintmachine.REInstruction;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.serialization.DeserializeException;
import com.radixdlt.store.TxnIndex;
import com.radixdlt.store.CMStore;
import com.radixdlt.store.ReadableAddrs;

import java.nio.ByteBuffer;
import java.util.Optional;

/**
 * CMStore which allows one to parse transactions given
 * the append log as the backend store.
 */
public final class LogCMStore implements CMStore {
	private final TxnIndex txnIndex;
	private final ReadableAddrs readableAddrs;

	@Inject
	public LogCMStore(
		TxnIndex txnIndex,
		ReadableAddrs readableAddrs
	) {
		this.txnIndex = txnIndex;
		this.readableAddrs = readableAddrs;
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
	public Optional<Particle> loadAddr(Transaction tx, REAddr rri) {
		return readableAddrs.loadAddr(tx, rri);
	}

	@Override
	public Optional<Particle> loadUpParticle(Transaction dbTxn, SubstateId substateId) {
		var txnId = substateId.getTxnId();
		return txnIndex.get(txnId)
			.flatMap(txn -> {
				var buf = ByteBuffer.wrap(txn.getPayload());
				var index = substateId.getIndex().orElseThrow();
				int cur = 0;
				while (buf.hasRemaining()) {
					try {
						var i = REInstruction.readFrom(txn, cur, buf);
						if (cur == index) {
							if (i.getMicroOp() != REInstruction.REMicroOp.UP) {
								return Optional.empty();
							}
							Substate s = i.getData();
							return Optional.of(s.getParticle());
						}
					} catch (DeserializeException e) {
						throw new IllegalStateException("Cannot deserialize instruction@" + cur, e);
					}
					cur++;
				}

				return Optional.empty();
			});
	}

	@Override
	public SubstateCursor openIndexedCursor(Transaction dbTransaction, Class<? extends Particle> particleClass) {
		throw new UnsupportedOperationException();
	}
}
