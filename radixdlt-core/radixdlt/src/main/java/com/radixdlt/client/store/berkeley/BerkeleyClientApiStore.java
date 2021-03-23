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

package com.radixdlt.client.store.berkeley;

import com.radixdlt.store.berkeley.BerkeleyLedgerEntryStore;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.inject.Inject;
import com.radixdlt.atom.SpunParticle;
import com.radixdlt.atommodel.tokens.TransferrableTokensParticle;
import com.radixdlt.atommodel.tokens.UnallocatedTokensParticle;
import com.radixdlt.client.store.ClientApiStore;
import com.radixdlt.client.store.ClientApiStoreException;
import com.radixdlt.client.store.TokenBalance;
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.constraintmachine.Spin;
import com.radixdlt.environment.EventProcessor;
import com.radixdlt.environment.ScheduledEventDispatcher;
import com.radixdlt.identifiers.RadixAddress;
import com.radixdlt.serialization.DeserializeException;
import com.radixdlt.serialization.Serialization;
import com.radixdlt.store.DatabaseEnvironment;
import com.radixdlt.utils.RadixConstants;
import com.radixdlt.utils.functional.Result;
import com.sleepycat.je.Cursor;
import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.OperationStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.google.common.primitives.UnsignedBytes.lexicographicalComparator;
import static com.radixdlt.serialization.DsonOutput.Output;

public class BerkeleyClientApiStore implements ClientApiStore {
	private static final Logger log = LogManager.getLogger();

	private static final String EXECUTED_TRANSACTIONS_DB = "radix.executed_transactions_db";
	private static final String BALANCE_DB = "radix.balance_db";
	private static final long DEFAULT_FLUSH_INTERVAL = 100L;

	private final DatabaseEnvironment dbEnv;
	private final BerkeleyLedgerEntryStore store;
	private final Serialization serialization;
	private final ScheduledEventDispatcher<ScheduledParticleFlush> scheduledFlushEventDispatcher;
	private final StackingCollector<SpunParticle> particleCollector = StackingCollector.create();

	private Database executedTransactionsDatabase;
	private Database balanceDatabase;

	@Inject
	public BerkeleyClientApiStore(
		DatabaseEnvironment dbEnv,
		BerkeleyLedgerEntryStore store,
		Serialization serialization,
		ScheduledEventDispatcher<ScheduledParticleFlush> scheduledFlushEventDispatcher
	) {
		this.dbEnv = dbEnv;
		this.store = store;
		this.serialization = serialization;
		this.scheduledFlushEventDispatcher = scheduledFlushEventDispatcher;

		open();
	}

	@Override
	public Result<List<TokenBalance>> getTokenBalances(RadixAddress address) {
		try (Cursor cursor = balanceDatabase.openCursor(null, null)) {
			var key = asKey(address.toString());
			var data = entry();

			if (cursor.getSearchKeyRange(key, data, null) != OperationStatus.SUCCESS) {
				return Result.ok(List.of());
			}

			var list = new ArrayList<TokenBalance>();

			do {
				var success = deserializeBalanceEntry(data.getData())
					.map(TokenBalance::from)
					.onSuccess(list::add)
					.isSuccess();

				if (!success) {
					log.error("Error deserializing existing balance while scanning DB for address {}", address);
				}
			}
			while (cursor.getNext(key, data, null) == OperationStatus.SUCCESS);

			return Result.ok(list);
		}
	}

	private Result<BalanceEntry> deserializeBalanceEntry(byte[] data) {
		try {
			return Result.ok(serialization.fromDson(data, BalanceEntry.class));
		} catch (DeserializeException e) {
			return Result.fail("Unable to deserialize value from DB.");
		}
	}

	private void open() {
		var config = new DatabaseConfig()
			.setAllowCreate(true)
			.setTransactional(true)
			.setKeyPrefixing(true)
			.setBtreeComparator(lexicographicalComparator());

		try {
			// This SuppressWarnings here is valid, as ownership of the underlying
			// resource is not changed here, the resource is just accessed.
			@SuppressWarnings("resource")
			var env = dbEnv.getEnvironment();
			balanceDatabase = env.openDatabase(null, BALANCE_DB, config);
			executedTransactionsDatabase = env.openDatabase(null, EXECUTED_TRANSACTIONS_DB, config);

			if (System.getProperty("db.check_integrity", "1").equals("1")) {
				//TODO: Implement recovery, basically should be the same as fresh DB handling
			}

			if (executedTransactionsDatabase.count() == 0) {
				//Fresh DB, rebuild from log
				rebuildDatabase();
			}

			//TODO: switch to generalized committed atoms notifications.
			store.onParticleCommit(this::newParticle);
		} catch (Exception e) {
			throw new ClientApiStoreException("Error while opening databases", e);
		}
	}

	@Override
	public void storeCollectedParticles() {
		synchronized (particleCollector) {
			// Ensure that all storing is sequential
			particleCollector.consumeCollected(this::storeSingleParticle);
		}
	}

	@Override
	public EventProcessor<ScheduledParticleFlush> particleFlushProcessor() {
		return flush -> {
			storeCollectedParticles();
			scheduledFlushEventDispatcher.dispatch(ScheduledParticleFlush.create(), DEFAULT_FLUSH_INTERVAL);
		};
	}

	public void close() {
		//TODO: temporary hack
		store.onParticleCommit(null); //Stop watching for new atoms
		storeCollectedParticles();

		safeClose(executedTransactionsDatabase);
		safeClose(balanceDatabase);
	}

	private void safeClose(Database database) {
		if (database != null) {
			database.close();
		}
	}

	private void rebuildDatabase() {
		log.info("Database rebuilding is started");

		store.forEach(this::storeSingleParticle);

		log.info("Database rebuilding is finished successfully");
	}

	private void newParticle(SpunParticle particle) {
		particleCollector.push(particle);
	}

	private void storeSingleParticle(SpunParticle spunParticle) {
		if (spunParticle.getSpin() == Spin.DOWN) {
			storeSingleDownParticle(spunParticle.getParticle());
		} else {
			storeSingleUpParticle(spunParticle.getParticle());
		}
	}

	private void storeSingleUpParticle(Particle particle) {
		toBalanceEntry(particle).ifPresent(balanceEntry -> {
			var key = asKey(balanceEntry.toKey());
			var value = serializeBalanceEntry(entry(), balanceEntry);

			mergeBalances(key, value, balanceEntry.negate());
		});
	}

	private void storeSingleDownParticle(Particle particle) {
		toBalanceEntry(particle).ifPresent(balanceEntry -> {
			var key = asKey(balanceEntry.toKey());

			mergeBalances(key, entry(), balanceEntry);
		});
	}

	private void mergeBalances(DatabaseEntry key, DatabaseEntry value, BalanceEntry balanceEntry) {
		var oldValue = entry();
		var status = balanceDatabase.get(null, key, oldValue, null);

		if (status == OperationStatus.NOTFOUND) {
			// Negate the supply value
			serializeBalanceEntry(value, balanceEntry.negate());
		} else if (status == OperationStatus.SUCCESS) {
			// Merge with existing balance
			var success = deserializeBalanceEntry(oldValue.getData())
				.map(existingBalance -> existingBalance.subtract(balanceEntry))
				.onSuccess(entry -> serializeBalanceEntry(value, entry))
				.isSuccess();

			if (!success) {
				log.error("Error deserializing existing balance for {}", balanceEntry);
			}
		}

		status = balanceDatabase.put(null, key, value);

		if (status != OperationStatus.SUCCESS) {
			log.error("Error while calculating merged balance {}", balanceEntry);
		}
	}

	private DatabaseEntry serializeBalanceEntry(DatabaseEntry value, BalanceEntry entry) {
		value.setData(serialization.toDson(entry, Output.ALL));
		return value;
	}

	private DatabaseEntry asKey(String key) {
		return entry(key.getBytes(RadixConstants.STANDARD_CHARSET));
	}

	private Optional<BalanceEntry> toBalanceEntry(Particle p) {
		if (p instanceof TransferrableTokensParticle) {
			var a = (TransferrableTokensParticle) p;
			return Optional.of(BalanceEntry.create(
				a.getAddress(),
				null,
				a.getTokDefRef(),
				a.getGranularity(),
				a.getAmount()
			));
		} else if (p instanceof UnallocatedTokensParticle) {
			var a = (UnallocatedTokensParticle) p;
			return Optional.of(BalanceEntry.create(
				a.getAddress(),
				null,
				a.getTokDefRef(),
				a.getGranularity(),
				a.getAmount()
			));
		}

		return Optional.empty();
	}

	static DatabaseEntry entry(byte[] data) {
		return new DatabaseEntry(data);
	}

	private static DatabaseEntry entry() {
		return new DatabaseEntry();
	}
}
