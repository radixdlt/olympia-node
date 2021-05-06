/*
 * (C) Copyright 2021 Radix DLT Ltd
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

import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.radixdlt.network.p2p.NodeId;
import com.radixdlt.network.p2p.addressbook.AddressBookEntry;
import com.radixdlt.network.p2p.addressbook.AddressBookPersistence;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.radixdlt.counters.SystemCounters;
import com.radixdlt.counters.SystemCounters.CounterType;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.Serialization;
import com.radixdlt.store.DatabaseEnvironment;
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

/**
 * Persistence for address book entries.
 */
@Singleton
public final class BerkeleyAddressBookPersistence implements AddressBookPersistence  {
	private static final Logger log = LogManager.getLogger();

	private final Serialization serialization;
	private final DatabaseEnvironment dbEnv;
	private final SystemCounters systemCounters;
	private Database entriesDb;

	@Inject
	public BerkeleyAddressBookPersistence(
		Serialization serialization,
		DatabaseEnvironment dbEnv,
		SystemCounters systemCounters
	) {
		this.serialization = Objects.requireNonNull(serialization);
		this.dbEnv = Objects.requireNonNull(dbEnv);
		this.systemCounters = Objects.requireNonNull(systemCounters);

		this.open();
	}

	@Override
	public void open() {
		final var config = new DatabaseConfig();
		config.setAllowCreate(true);

		try {
			this.entriesDb = this.dbEnv.getEnvironment().openDatabase(null, "address_book_entries", config);
		} catch (DatabaseException | IllegalArgumentException | IllegalStateException ex) {
        	throw new IllegalStateException("while opening database", ex);
		}
	}

	@Override
	public void reset() {
		Transaction transaction = null;

		try {
			transaction = this.dbEnv.getEnvironment().beginTransaction(null, new TransactionConfig().setReadUncommitted(true));
			this.dbEnv.getEnvironment().truncateDatabase(transaction, "address_book_entries", false);
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
		if (this.entriesDb != null) {
			this.entriesDb.close();
			this.entriesDb = null;
		}
	}

	@Override
	public boolean saveEntry(AddressBookEntry entry) {
		log.info("Berkeley saveEntry: " + entry);
		final var start = System.nanoTime();
		try {
			final var key = new DatabaseEntry(entry.getNodeId().getPublicKey().getBytes());
			final var value = new DatabaseEntry(serialization.toDson(entry, Output.PERSIST));

			if (entriesDb.put(null, key, value) == OperationStatus.SUCCESS) {
				addBytesWrite(key.getSize() + value.getSize());
				return true;
			}

			return false;
		} finally {
			addTime(start);
		}
	}

	@Override
	public boolean removeEntry(NodeId nodeId) {
		final var start = System.nanoTime();
		try {
			final var key = new DatabaseEntry(nodeId.getPublicKey().getBytes());

			if (entriesDb.delete(null, key) == OperationStatus.SUCCESS) {
				systemCounters.increment(CounterType.COUNT_BDB_ADDRESS_BOOK_DELETES);
				return true;
			}
			return false;
		} finally {
			addTime(start);
		}
	}

	@Override
	public ImmutableList<AddressBookEntry> getAllEntries() {
		final var start = System.nanoTime();
		try {
			try (var cursor = this.entriesDb.openCursor(null, null)) {
				final var key = new DatabaseEntry();
				final var value = new DatabaseEntry();

				final var builder = ImmutableList.<AddressBookEntry>builder();
				while (cursor.getNext(key, value, LockMode.DEFAULT) == OperationStatus.SUCCESS) {
					addBytesRead(key.getSize() + value.getSize());
					final var entry = serialization.fromDson(value.getData(), AddressBookEntry.class);
					builder.add(entry);
				}

				return builder.build();
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
}
