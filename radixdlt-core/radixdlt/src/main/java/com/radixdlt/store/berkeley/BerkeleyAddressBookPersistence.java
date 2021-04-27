/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 */

package com.radixdlt.store.berkeley;

/*
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.radixdlt.counters.SystemCounters;
import com.radixdlt.counters.SystemCounters.CounterType;
import com.radixdlt.identifiers.EUID;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.Serialization;
import com.radixdlt.store.DatabaseEnvironment;
import com.sleepycat.je.Cursor;
import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.DatabaseNotFoundException;
import com.sleepycat.je.LockMode;
import com.sleepycat.je.OperationStatus;
import com.sleepycat.je.Transaction;
import com.sleepycat.je.TransactionConfig;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Objects;
import java.util.function.Consumer;
*/

/**
 * Persistence for peers.
 */
public class BerkeleyAddressBookPersistence {
	/*
	private static final Logger log = LogManager.getLogger();

	private final Serialization serialization;
	private final DatabaseEnvironment dbEnv;
	private final SystemCounters systemCounters;
	private Database peersByNidDB;


	public BerkeleyAddressBookPersistence(
		Serialization serialization,
		DatabaseEnvironment dbEnv,
		SystemCounters systemCounters
	) {
		this.serialization = Objects.requireNonNull(serialization);
		this.dbEnv = Objects.requireNonNull(dbEnv);
		this.systemCounters = Objects.requireNonNull(systemCounters);
	}

	@Override
	public void start() {
		DatabaseConfig config = new DatabaseConfig();
		config.setAllowCreate(true);

		try {
			this.peersByNidDB = this.dbEnv.getEnvironment().openDatabase(null, "peers_by_nid", config);
		} catch (DatabaseException | IllegalArgumentException | IllegalStateException ex) {
        	throw new IllegalStateException("while opening database", ex);
		}
	}

	@Override
	public void reset() {
		Transaction transaction = null;

		try {
			transaction = this.dbEnv.getEnvironment().beginTransaction(null, new TransactionConfig().setReadUncommitted(true));
			this.dbEnv.getEnvironment().truncateDatabase(transaction, "peers_by_nid", false);
			transaction.commit();
		} catch (DatabaseNotFoundException dsnfex) {
			if (transaction != null) {
				transaction.abort();
			}
			log.warn(dsnfex.getMessage());
		} catch (Exception ex) {
			if (transaction != null) {
				transaction.abort();
			}
			throw new IllegalStateException("while resetting database", ex);
		}
	}

	@Override
	public void close() {
		if (this.peersByNidDB != null) {
			this.peersByNidDB.close();
			this.peersByNidDB = null;
		}
	}

	@Override
	public boolean savePeer(PeerWithSystem peer) {
		final var start = System.nanoTime();
		try {
			DatabaseEntry key = new DatabaseEntry(peer.getNID().toByteArray());
			byte[] bytes = serialization.toDson(peer, Output.PERSIST);
			DatabaseEntry value = new DatabaseEntry(bytes);

			if (peersByNidDB.put(null, key, value) == OperationStatus.SUCCESS) {
				addBytesWrite(key.getSize() + value.getSize());
				return true;
			}

			return false;
		} finally {
			addTime(start);
		}
	}

	@Override
	public boolean deletePeer(EUID nid) {
		final var start = System.nanoTime();
		try {
			DatabaseEntry key = new DatabaseEntry(nid.toByteArray());

			if (peersByNidDB.delete(null, key) == OperationStatus.SUCCESS) {
				systemCounters.increment(CounterType.COUNT_BDB_ADDRESS_BOOK_DELETES);
				return true;
			}
			return false;
		} finally {
			addTime(start);
		}
	}

	@Override
	public void forEachPersistedPeer(Consumer<PeerWithSystem> c) {
		final var start = System.nanoTime();
		try {
			try (Cursor cursor = this.peersByNidDB.openCursor(null, null)) {
				DatabaseEntry key = new DatabaseEntry();
				DatabaseEntry value = new DatabaseEntry();

				while (cursor.getNext(key, value, LockMode.DEFAULT) == OperationStatus.SUCCESS) {
					addBytesRead(key.getSize() + value.getSize());
					PeerWithSystem peer = this.serialization.fromDson(value.getData(), PeerWithSystem.class);
					c.accept(peer);
				}
			} catch (IOException ex) {
				throw new UncheckedIOException("Error while loading database", ex);
			}
		} finally {
			addTime(start);
		}
	}

	private void addTime(long start) {
		final var elapsed = (System.nanoTime() - start + 500L) / 1000L;
		this.systemCounters.add(CounterType.ELAPSED_BDB_ADDRESS_BOOK, elapsed);
		this.systemCounters.increment(CounterType.COUNT_BDB_ADDRESS_BOOK_TOTAL);
	}

	private void addBytesRead(int bytesRead) {
		this.systemCounters.add(CounterType.COUNT_BDB_ADDRESS_BOOK_BYTES_READ, bytesRead);
	}

	private void addBytesWrite(int bytesWrite) {
		this.systemCounters.add(CounterType.COUNT_BDB_ADDRESS_BOOK_BYTES_WRITE, bytesWrite);
	}
	 */
}

