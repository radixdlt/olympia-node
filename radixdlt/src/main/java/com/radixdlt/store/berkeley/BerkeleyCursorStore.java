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

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.radixdlt.identifiers.EUID;
import com.radixdlt.consensus.tempo.TempoException;
import com.radixdlt.store.CursorStore;
import com.radixdlt.utils.Longs;
import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.DatabaseNotFoundException;
import com.sleepycat.je.Environment;
import com.sleepycat.je.LockMode;
import com.sleepycat.je.OperationStatus;
import com.sleepycat.je.Transaction;
import com.sleepycat.je.TransactionConfig;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.radix.database.DatabaseEnvironment;

import java.util.Objects;
import java.util.OptionalLong;

@Singleton
public final class BerkeleyCursorStore implements CursorStore {
	private static final String LC_CURSOR_STORE_NAME = "tempo2.sync.iterative.cursors";
	private static final Logger logger = LogManager.getLogger();

	private final DatabaseEnvironment dbEnv;
	private Database cursors;

	@Inject
	public BerkeleyCursorStore(DatabaseEnvironment dbEnv) {
		this.dbEnv = Objects.requireNonNull(dbEnv, "dbEnv is required");

		this.open();
	}

	private void fail(String message) {
		logger.error(message);
		throw new TempoException(message);
	}

	private void fail(String message, Exception cause) {
		logger.error(message, cause);
		throw new TempoException(message, cause);
	}

	private void open() {
		DatabaseConfig primaryConfig = new DatabaseConfig();
		primaryConfig.setAllowCreate(true);
		primaryConfig.setTransactional(true);

		try {
			// This SuppressWarnings here is valid, as ownership of the underlying
			// resource is not changed here, the resource is just accessed.
			@SuppressWarnings("resource")
			Environment env = this.dbEnv.getEnvironment();
			this.cursors = env.openDatabase(null, LC_CURSOR_STORE_NAME, primaryConfig);
		} catch (Exception e) {
			throw new TempoException("Error while opening database", e);
		}

		if (System.getProperty("db.check_integrity", "1").equals("1")) {
			// TODO implement intergrity check
		}
	}

	@Override
	public void reset() {
		dbEnv.withLock(() -> {
			Transaction transaction = null;
			try {
				// This SuppressWarnings here is valid, as ownership of the underlying
				// resource is not changed here, the resource is just accessed.
				@SuppressWarnings("resource")
				Environment env = this.dbEnv.getEnvironment();
				transaction = env.beginTransaction(null, new TransactionConfig().setReadUncommitted(true));
				env.truncateDatabase(transaction, LC_CURSOR_STORE_NAME, false);
				transaction.commit();
			} catch (DatabaseNotFoundException e) {
				if (transaction != null) {
					transaction.abort();
				}
				logger.warn("Error while resetting database, database not found", e);
			} catch (Exception e) {
				if (transaction != null) {
					transaction.abort();
				}
				throw new TempoException("Error while resetting databases", e);
			}
		});
	}

	@Override
	public void close() {
		if (this.cursors != null) {
			this.cursors.close();
		}
	}

	@Override
	public void put(EUID nid, long cursor) {
		Transaction transaction = dbEnv.getEnvironment().beginTransaction(null, null);
		try {
			DatabaseEntry key = new DatabaseEntry(toPKey(nid));
			DatabaseEntry value = new DatabaseEntry(Longs.toByteArray(cursor));

			OperationStatus status = this.cursors.put(transaction, key, value);
			if (status != OperationStatus.SUCCESS) {
				fail("Database returned status " + status + " for put operation");
			}

			transaction.commit();
		} catch (Exception e) {
			transaction.abort();
			fail("Error while storing cursor for '" + nid + "'", e);
		}
	}

	@Override
	public OptionalLong get(EUID nid) {
		try {
			DatabaseEntry key = new DatabaseEntry(toPKey(nid));
			DatabaseEntry value = new DatabaseEntry();

			OperationStatus status = this.cursors.get(null, key, value, LockMode.DEFAULT);
			if (status == OperationStatus.NOTFOUND) {
				return OptionalLong.empty();
			} else if (status != OperationStatus.SUCCESS) {
				fail("Database returned status " + status + " for get operation");
			} else {
				long cursor = Longs.fromByteArray(value.getData());
				return OptionalLong.of(cursor);
			}
		} catch (Exception e) {
			fail("Error while getting cursor for '" + nid + "'", e);
		}

		return OptionalLong.empty();
	}

	private byte[] toPKey(EUID nid) {
		return nid.toByteArray();
	}
}
