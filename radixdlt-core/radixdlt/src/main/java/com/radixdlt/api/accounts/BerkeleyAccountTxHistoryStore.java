/*
 * Copyright 2021 Radix DLT Ltd incorporated in England.
 * Licensed under the Radix License, Version 1.0 (the "License"); you may not use this
 * file except in compliance with the License. You may obtain a copy of the License at:
 *
 * radixfoundation.org/licenses/LICENSE-v1
 * The Licensor hereby grants permission for the Canonical version of the Work to be
 * published, distributed and used under or by reference to the Licensor’s trademark
 * Radix ® and use of any unregistered trade names, logos or get-up.
 *
 * The Licensor provides the Work (and each Contributor provides its Contributions) on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied,
 * including, without limitation, any warranties or conditions of TITLE, NON-INFRINGEMENT,
 * MERCHANTABILITY, or FITNESS FOR A PARTICULAR PURPOSE.
 *
 * Whilst the Work is capable of being deployed, used and adopted (instantiated) to create
 * a distributed ledger it is your responsibility to test and validate the code, together
 * with all logic and performance of that code under all foreseeable scenarios.
 *
 * The Licensor does not make or purport to make and hereby excludes liability for all
 * and any representation, warranty or undertaking in any form whatsoever, whether express
 * or implied, to any entity or person, including any representation, warranty or
 * undertaking, as to the functionality security use, value or other characteristics of
 * any distributed ledger nor in respect the functioning or value of any tokens which may
 * be created stored or transferred using the Work. The Licensor does not warrant that the
 * Work or any use of the Work complies with any law or regulation in any territory where
 * it may be implemented or used or that it will be appropriate for any specific purpose.
 *
 * Neither the licensor nor any current or former employees, officers, directors, partners,
 * trustees, representatives, agents, advisors, contractors, or volunteers of the Licensor
 * shall be liable for any direct or indirect, special, incidental, consequential or other
 * losses of any kind, in tort, contract or otherwise (including but not limited to loss
 * of revenue, income or profits, or loss of use or data, or loss of reputation, or loss
 * of any economic or other opportunity of whatsoever nature or howsoever arising), arising
 * out of or in connection with (without limitation of any use, misuse, of any ledger system
 * or use made or its functionality or any performance or operation of any code or protocol
 * caused by bugs or programming or logic errors or otherwise);
 *
 * A. any offer, purchase, holding, use, sale, exchange or transmission of any
 * cryptographic keys, tokens or assets created, exchanged, stored or arising from any
 * interaction with the Work;
 *
 * B. any failure in a transmission or loss of any token or assets keys or other digital
 * artefacts due to errors in transmission;
 *
 * C. bugs, hacks, logic errors or faults in the Work or any communication;
 *
 * D. system software or apparatus including but not limited to losses caused by errors
 * in holding or transmitting tokens by any third-party;
 *
 * E. breaches or failure of security including hacker attacks, loss or disclosure of
 * password, loss of private key, unauthorised use or misuse of such passwords or keys;
 *
 * F. any losses including loss of anticipated savings or other benefits resulting from
 * use of the Work or any changes to the Work (however implemented).
 *
 * You are solely responsible for; testing, validating and evaluation of all operation
 * logic, functionality, security and appropriateness of using the Work for any commercial
 * or non-commercial purpose and for any reproduction or redistribution by You of the
 * Work. You assume all risks associated with Your use of the Work and the exercise of
 * permissions under this License.
 */

package com.radixdlt.api.accounts;

import com.google.common.collect.Streams;
import com.radixdlt.accounting.REResourceAccounting;
import com.radixdlt.application.tokens.Bucket;
import com.radixdlt.constraintmachine.REProcessedTxn;
import com.radixdlt.constraintmachine.RawSubstateBytes;
import com.radixdlt.constraintmachine.SystemMapKey;
import com.radixdlt.identifiers.AID;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.store.DatabaseEnvironment;
import com.radixdlt.store.berkeley.BerkeleyAdditionalStore;
import com.radixdlt.utils.Longs;
import com.radixdlt.utils.Pair;
import com.sleepycat.je.Cursor;
import com.sleepycat.je.CursorConfig;
import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.Get;
import com.sleepycat.je.OperationStatus;
import com.sleepycat.je.Transaction;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

import static com.google.common.primitives.UnsignedBytes.lexicographicalComparator;
import static com.sleepycat.je.OperationStatus.NOTFOUND;
import static com.sleepycat.je.OperationStatus.SUCCESS;

public class BerkeleyAccountTxHistoryStore implements BerkeleyAdditionalStore {
	private Database accountTxHistory;

	@Override
	public void open(DatabaseEnvironment dbEnv) {
		var env = dbEnv.getEnvironment();
		this.accountTxHistory = env.openDatabase(null, "radix.account_txn_history", new DatabaseConfig()
			.setAllowCreate(true)
			.setTransactional(true)
			.setKeyPrefixing(true)
			.setBtreeComparator(lexicographicalComparator())
		);
	}

	@Override
	public void close() {
		if (accountTxHistory != null) {
			accountTxHistory.close();
		}
	}

	private Iterator<Pair<AID, Long>> createReverseIteratorFromCursor(REAddr addr, Long offset, Cursor cursor) {
		return new Iterator<>() {
			private final DatabaseEntry key;
			private final DatabaseEntry value = new DatabaseEntry();
			private OperationStatus status;
			{
				if (offset != null) {
					key = key(addr, offset);
					status = cursor.get(key, value, Get.SEARCH, null) != null ? SUCCESS : OperationStatus.NOTFOUND;
				} else {
					var maybeLast = lastOffset(cursor, value, addr);
					if (maybeLast.isPresent()) {
						key = maybeLast.get();
						status = SUCCESS;
					} else {
						key = new DatabaseEntry();
						status = NOTFOUND;
					}
				}
			}

			@Override
			public boolean hasNext() {
				return status == SUCCESS;
			}

			@Override
			public Pair<AID, Long> next() {
				if (status != SUCCESS) {
					throw new NoSuchElementException();
				}
				var nextOffset = Longs.fromByteArray(key.getData(), REAddr.PUB_KEY_BYTES);
				var nextValue = AID.from(value.getData());
				var curStatus = cursor.getPrev(key, value, null);
				status = curStatus == SUCCESS && keyIsREAddr(key, addr) ? SUCCESS : NOTFOUND;
				return Pair.of(nextValue, nextOffset);
			}
		};
	}

	public Stream<Pair<AID, Long>> getTxnIdsAssociatedWithAccount(REAddr addr, Long offset) {
		var cursor = accountTxHistory.openCursor(null, null);
		var iterator = createReverseIteratorFromCursor(addr, offset, cursor);
		return Streams.stream(iterator)
			.onClose(cursor::close);
	}

	private static Stream<REAddr> getAccountsAssociatedWithTxn(REProcessedTxn txn) {
		// Only save user transactions
		if (txn.getSignedBy().isEmpty()) {
			return Stream.empty();
		}
		var accounting = REResourceAccounting.compute(txn.stateUpdates());
		return accounting.bucketAccounting().keySet().stream()
			.map(Bucket::getOwner)
			.filter(Objects::nonNull)
			.distinct();
	}

	private DatabaseEntry key(REAddr addr, Long offset) {
		var addrBytes = addr.getBytes();
		var buf = ByteBuffer.allocate(addrBytes.length + (offset != null ? Long.BYTES : 0));
		buf.put(addrBytes);
		if (offset != null) {
			buf.putLong(offset);
		}
		return new DatabaseEntry(buf.array());
	}

	private static boolean keyIsREAddr(DatabaseEntry key, REAddr addr) {
		return Arrays.equals(key.getData(), 0, REAddr.PUB_KEY_BYTES, addr.getBytes(), 0, REAddr.PUB_KEY_BYTES);
	}

	private Optional<DatabaseEntry> lastOffset(Cursor cursor, DatabaseEntry value, REAddr addr) {
		var key = key(addr, Long.MAX_VALUE);
		cursor.getSearchKeyRange(key, null, null);
		var result = cursor.getPrev(key, value, null);
		if (result == OperationStatus.NOTFOUND) {
			return Optional.empty();
		} else if (result == SUCCESS) {
			return keyIsREAddr(key, addr) ? Optional.of(key) : Optional.empty();
		} else {
			throw new IllegalStateException("Unexpected result " + result);
		}
	}

	private long nextOffset(Transaction dbTxn, REAddr addr) {
		try (var cursor = accountTxHistory.openCursor(dbTxn, CursorConfig.READ_UNCOMMITTED)) {
			return lastOffset(cursor, null, addr)
				.map(e -> Longs.fromByteArray(e.getData(), REAddr.PUB_KEY_BYTES) + 1)
				.orElse(0L);
		}
	}

	private void storeTxnForAccount(Transaction dbTxn, REProcessedTxn txn, REAddr addr) {
		var offset = nextOffset(dbTxn, addr);
		var key = key(addr, offset);
		var value = new DatabaseEntry(txn.getTxnId().getBytes());
		var status = accountTxHistory.putNoOverwrite(dbTxn, key, value);
		if (status != OperationStatus.SUCCESS) {
			throw new IllegalStateException("Unable to store account transaction(off: " + offset + "): " + status);
		}
	}

	@Override
	public void process(Transaction dbTxn, REProcessedTxn txn, long stateVersion, Function<SystemMapKey, Optional<RawSubstateBytes>> mapper) {
		getAccountsAssociatedWithTxn(txn)
			.forEach(addr -> storeTxnForAccount(dbTxn, txn, addr));
	}
}
