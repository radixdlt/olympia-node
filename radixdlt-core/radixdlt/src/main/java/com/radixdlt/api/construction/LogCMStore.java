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
import com.radixdlt.atom.CloseableCursor;
import com.radixdlt.atom.SubstateId;
import com.radixdlt.constraintmachine.SubstateIndex;
import com.radixdlt.constraintmachine.REInstruction;
import com.radixdlt.constraintmachine.RawSubstateBytes;
import com.radixdlt.constraintmachine.exceptions.TxnParseException;
import com.radixdlt.engine.parser.REParser;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.store.TxnIndex;
import com.radixdlt.store.CMStore;
import com.radixdlt.store.ResourceStore;

import java.nio.ByteBuffer;
import java.util.Optional;

/**
 * CMStore which allows one to parse transactions given
 * the append log as the backend store.
 * TODO: Remove this class
 */
public final class LogCMStore implements CMStore {
	private final TxnIndex txnIndex;
	private final ResourceStore readableAddrs;
	private final REParser reParser;

	@Inject
	public LogCMStore(
		TxnIndex txnIndex,
		ResourceStore readableAddrs,
		REParser reParser
	) {
		this.txnIndex = txnIndex;
		this.readableAddrs = readableAddrs;
		this.reParser = reParser;
	}

	@Override
	public boolean isVirtualDown(SubstateId substateId) {
		return false;
	}

	@Override
	public Optional<ByteBuffer> loadResource(REAddr rri) {
		return readableAddrs.loadResource(rri);
	}

	@Override
	public Optional<ByteBuffer> loadUpParticle(SubstateId substateId) {
		var txnId = substateId.getTxnId();
		return txnIndex.get(txnId)
			.flatMap(txn -> {
				var index = substateId.getIndex().orElseThrow();
				try {
					var instructions = reParser.parse(txn).instructions();
					return instructions.stream()
						.filter(i -> i.getMicroOp() == REInstruction.REMicroOp.UP)
						.skip(index)
						.findFirst()
						.map(i -> i.getDataByteBuffer());
				} catch (TxnParseException e) {
					throw new IllegalStateException("Cannot deserialize txn", e);
				}
			});
	}

	@Override
	public CloseableCursor<RawSubstateBytes> openIndexedCursor(SubstateIndex index) {
		throw new UnsupportedOperationException();
	}
}
