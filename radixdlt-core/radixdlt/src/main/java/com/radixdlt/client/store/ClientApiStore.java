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
import com.radixdlt.atom.Atom;
import com.radixdlt.atommodel.tokens.FixedSupplyTokenDefinitionParticle;
import com.radixdlt.atommodel.tokens.MutableSupplyTokenDefinitionParticle;
import com.radixdlt.atommodel.tokens.StakedTokensParticle;
import com.radixdlt.atommodel.tokens.TransferrableTokensParticle;
import com.radixdlt.atommodel.tokens.UnallocatedTokensParticle;
import com.radixdlt.atommodel.unique.UniqueParticle;
import com.radixdlt.atommodel.validators.RegisteredValidatorParticle;
import com.radixdlt.atommodel.validators.UnregisteredValidatorParticle;
import com.radixdlt.atomos.RRIParticle;
import com.radixdlt.constraintmachine.CMMicroInstruction;
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.constraintmachine.Spin;
import com.radixdlt.identifiers.RadixAddress;
import com.radixdlt.store.DatabaseEnvironment;
import com.radixdlt.store.LedgerEntryStore;
import com.radixdlt.utils.Pair;
import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import static com.google.common.primitives.UnsignedBytes.lexicographicalComparator;
import static com.radixdlt.utils.ThreadFactories.daemonThreads;

import static java.util.concurrent.Executors.newSingleThreadExecutor;

public class ClientApiStore {
	private static final Logger log = LogManager.getLogger();

	private static final String DESTINATION_DB_NAME = "radix.destination_db";
	private static final String BALANCE_DB_NAME = "radix.balance_db";

	private final DatabaseEnvironment dbEnv;
	private final LedgerEntryStore store;
	private final StackingCollector<Pair<Atom, Long>> atomCollector = StackingCollector.create();
	private final ExecutorService executorService = newSingleThreadExecutor(daemonThreads("ClientApiStore"));

	private Database destinationDatabase;
	private Database balanceDatabase;

	@Inject
	public ClientApiStore(DatabaseEnvironment dbEnv, LedgerEntryStore store) {
		this.dbEnv = dbEnv;
		this.store = store;
		store.onAtomCommit(this::newAtom);

		open();
	}

	public void close() {
		store.onAtomCommit(null); //Stop watching for new atoms
		storeAtoms();

		safeClose(this.destinationDatabase);
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
			.setSortedDuplicates(true)
			.setBtreeComparator(lexicographicalComparator());

		try {
			// This SuppressWarnings here is valid, as ownership of the underlying
			// resource is not changed here, the resource is just accessed.
			@SuppressWarnings("resource")
			var env = dbEnv.getEnvironment();
			destinationDatabase = env.openDatabase(null, DESTINATION_DB_NAME, config);
			balanceDatabase = env.openDatabase(null, BALANCE_DB_NAME, config);

			if (System.getProperty("db.check_integrity", "1").equals("1")) {
				//TODO: Implement recovery, basically should be the same as fresh DB handling
			}

			if (destinationDatabase.count() == 0) {
				//Fresh DB, rebuild from log
				rebuildDatabase();
			}
		} catch (Exception e) {
			throw new ClientApiStoreException("Error while opening databases", e);
		}
	}

	private void storeAtoms() {
		atomCollector.consumeCollected(this::storeSingleAtom);
	}

	private void newAtom(Atom clientAtom, Long offset) {
		atomCollector.push(Pair.of(clientAtom, offset));
		executorService.submit(this::storeAtoms);
	}

	private void rebuildDatabase() {
		log.info("Database rebuilding is started");
		dbEnv.getEnvironment().truncateDatabase(null, DESTINATION_DB_NAME, false);

		var finished = new AtomicBoolean(false);

		store.forEach((atom, offset) -> {
			if (offset >= 0) {
				storeSingleAtom(Pair.of(atom, offset));
			} else {
				finished.set(true);
			}
		});

		if (!finished.get()) {
			log.info("Database rebuilding is failed. Not all atoms were received from atom store.");
			throw new IllegalStateException("Unable to rebuild indices, not all atoms are received");
		}

		log.info("Database rebuilding is finished successfully");
	}

	private void storeSingleAtom(Pair<Atom, Long> clientAtom) {
		//extract destinations
		var addresses = clientAtom.getFirst().uniqueInstructions()
			.filter(i -> i.getNextSpin() == Spin.UP)
			.map(CMMicroInstruction::getParticle)
			.map(this::toAddresses)
			.flatMap(Set::stream).collect(Collectors.toSet());
	}

	private Set<RadixAddress> toAddresses(Particle p) {
		Set<RadixAddress> addresses = new HashSet<>();

		if (p instanceof RRIParticle) {
			var a = (RRIParticle) p;
			addresses.add(a.getRri().getAddress());
		} else if (p instanceof StakedTokensParticle) {
			var a = (StakedTokensParticle) p;
			addresses.addAll(a.getAddresses());
		} else if (p instanceof TransferrableTokensParticle) {
			var a = (TransferrableTokensParticle) p;
			addresses.add(a.getAddress());
		} else if (p instanceof UnallocatedTokensParticle) {
			var a = (UnallocatedTokensParticle) p;
			addresses.add(a.getAddress());
		} else if (p instanceof RegisteredValidatorParticle) {
			var a = (RegisteredValidatorParticle) p;
			addresses.add(a.getAddress());
			addresses.addAll(a.getAllowedDelegators());
		} else if (p instanceof UnregisteredValidatorParticle) {
			var a = (UnregisteredValidatorParticle) p;
			addresses.add(a.getAddress());
		} else if (p instanceof FixedSupplyTokenDefinitionParticle) {
			var i = (FixedSupplyTokenDefinitionParticle) p;
			addresses.add(i.getRRI().getAddress());
		} else if (p instanceof MutableSupplyTokenDefinitionParticle) {
			var i = (MutableSupplyTokenDefinitionParticle) p;
			addresses.add(i.getRRI().getAddress());
		} else if (p instanceof UniqueParticle) {
			var i = (UniqueParticle) p;
			addresses.add(i.getRRI().getAddress());
		}

		return addresses;
	}
}
