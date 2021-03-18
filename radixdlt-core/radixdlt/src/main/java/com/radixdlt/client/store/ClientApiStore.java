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

package com.radixdlt.client.store;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.inject.Inject;
import com.radixdlt.atom.SpunParticle;
import com.radixdlt.atommodel.tokens.StakedTokensParticle;
import com.radixdlt.atommodel.tokens.TransferrableTokensParticle;
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.constraintmachine.Spin;
import com.radixdlt.serialization.Serialization;
import com.radixdlt.store.DatabaseEnvironment;
import com.radixdlt.store.LedgerEntryStore;
import com.radixdlt.utils.RadixConstants;
import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.DatabaseEntry;

import java.util.Optional;
import java.util.concurrent.ExecutorService;

import static com.google.common.primitives.UnsignedBytes.lexicographicalComparator;
import static com.radixdlt.serialization.DsonOutput.Output;
import static com.radixdlt.utils.ThreadFactories.daemonThreads;

import static java.util.concurrent.Executors.newSingleThreadExecutor;

public class ClientApiStore {
	private static final Logger log = LogManager.getLogger();

	private static final String EXECUTED_TRANSACTIONS_DATABASE = "radix.executed_transactions_db";
	private static final String BALANCE_DB_NAME = "radix.balance_db";

	private final DatabaseEnvironment dbEnv;
	private final LedgerEntryStore store;
	private final Serialization serialization;
	private final StackingCollector<SpunParticle> particleCollector = StackingCollector.create();
	private final ExecutorService executorService = newSingleThreadExecutor(daemonThreads("ClientApiStore"));

	private Database executedTransactionsDatabase;
	private Database balanceDatabase;

	@Inject
	public ClientApiStore(DatabaseEnvironment dbEnv, LedgerEntryStore store, Serialization serialization) {
		this.dbEnv = dbEnv;
		this.store = store;
		this.serialization = serialization;

		open();
	}

	public void close() {
		store.onParticleCommit(null); //Stop watching for new atoms
		storeParticles();

		safeClose(executedTransactionsDatabase);
		safeClose(balanceDatabase);
	}

	private void safeClose(Database database) {
		if (database != null) {
			database.close();
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
			balanceDatabase = env.openDatabase(null, BALANCE_DB_NAME, config);
			executedTransactionsDatabase = env.openDatabase(null, EXECUTED_TRANSACTIONS_DATABASE, config);

			if (System.getProperty("db.check_integrity", "1").equals("1")) {
				//TODO: Implement recovery, basically should be the same as fresh DB handling
			}

			if (executedTransactionsDatabase.count() == 0) {
				//Fresh DB, rebuild from log
				rebuildDatabase();
			}

			store.onParticleCommit(this::newParticle);
		} catch (Exception e) {
			throw new ClientApiStoreException("Error while opening databases", e);
		}
	}

	private void storeParticles() {
		particleCollector.consumeCollected(this::storeSingleParticle);
	}

	private void newParticle(SpunParticle particle) {
		particleCollector.push(particle);
		executorService.submit(this::storeParticles);
	}

	private void rebuildDatabase() {
		log.info("Database rebuilding is started");
		dbEnv.getEnvironment().truncateDatabase(null, EXECUTED_TRANSACTIONS_DATABASE, false);

		store.forEach(this::storeSingleParticle);

		log.info("Database rebuilding is finished successfully");
	}

	private void storeSingleParticle(SpunParticle spunParticle) {
		if (spunParticle.getSpin() == Spin.DOWN) {
			//TODO: current implementation does not cover unstaking during DB rebuild
			// because ledger DB does not store DOWN particles
			storeSingleDownParticle(spunParticle.getParticle());
		} else {
			storeSingleUpParticle(spunParticle.getParticle());
		}
	}

	private void storeSingleUpParticle(Particle particle) {
		toBalanceEntry(particle).ifPresent(entry -> {
			var particleBytes = serialization.toDson(entry, Output.PERSIST);
			var keyBytes = entry.accountName().getBytes(RadixConstants.STANDARD_CHARSET);
			balanceDatabase.put(null, entry(keyBytes), entry(particleBytes));
		});
	}

	private void storeSingleDownParticle(Particle particle) {
		toBalanceEntry(particle).ifPresent(entry -> {
			var keyBytes = entry.accountName().getBytes(RadixConstants.STANDARD_CHARSET);
			balanceDatabase.delete(null, entry(keyBytes));
		});
	}

	private Optional<BalanceEntry> toBalanceEntry(Particle p) {
		if (p instanceof StakedTokensParticle) {
			var a = (StakedTokensParticle) p;
			return Optional.of(BalanceEntry.create(
				a.getAddress(),
				a.getDelegateAddress(),
				a.getTokDefRef(),
				a.getGranularity(),
				a.getAmount()
			));
		} else if (p instanceof TransferrableTokensParticle) {
			var a = (TransferrableTokensParticle) p;
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
