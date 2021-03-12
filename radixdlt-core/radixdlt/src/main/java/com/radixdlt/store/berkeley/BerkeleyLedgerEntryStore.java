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

import com.google.common.hash.HashCode;
import com.radixdlt.consensus.Command;
import com.radixdlt.consensus.VerifiedLedgerHeaderAndProof;
import com.radixdlt.constraintmachine.CMMicroInstruction;
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.constraintmachine.Spin;
import com.radixdlt.crypto.Hasher;
import com.radixdlt.identifiers.EUID;
import com.radixdlt.ledger.VerifiedCommandsAndProof;
import com.radixdlt.middleware2.ClientAtom;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.SerializationUtils;
import com.radixdlt.statecomputer.CommittedAtom;
import com.radixdlt.store.StoreConfig;
import com.radixdlt.utils.Pair;
import com.sleepycat.je.Cursor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.radix.database.DatabaseEnvironment;

import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.radixdlt.consensus.bft.PersistentVertexStore;
import com.radixdlt.consensus.bft.VerifiedVertexStoreState;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.counters.SystemCounters.CounterType;
import com.radixdlt.identifiers.AID;
import com.radixdlt.serialization.DeserializeException;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.Serialization;
import com.radixdlt.store.LedgerEntryStore;
import com.radixdlt.store.LedgerEntryStoreResult;
import com.radixdlt.store.SearchCursor;
import com.radixdlt.store.Transaction;
import com.radixdlt.store.berkeley.atom.AppendLog;
import com.radixdlt.store.berkeley.atom.SimpleAppendLog;
import com.radixdlt.utils.Longs;
import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.DatabaseNotFoundException;
import com.sleepycat.je.LockMode;
import com.sleepycat.je.OperationStatus;
import com.sleepycat.je.SecondaryConfig;
import com.sleepycat.je.SecondaryDatabase;
import com.sleepycat.je.TransactionConfig;
import com.sleepycat.je.UniqueConstraintException;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.function.BiFunction;
import java.util.function.Supplier;

import static com.google.common.primitives.UnsignedBytes.lexicographicalComparator;
import static com.radixdlt.store.LedgerEntryStoreResult.ioFailure;
import static com.radixdlt.store.LedgerEntryStoreResult.success;
import static com.radixdlt.store.berkeley.BerkeleyTransaction.wrap;
import static com.radixdlt.utils.Longs.fromByteArray;
import static com.sleepycat.je.LockMode.DEFAULT;
import static com.sleepycat.je.LockMode.READ_COMMITTED;
import static com.sleepycat.je.OperationStatus.SUCCESS;

@Singleton
public final class BerkeleyLedgerEntryStore implements LedgerEntryStore, PersistentVertexStore {
	private static final Logger log = LogManager.getLogger();

	private static final String ATOM_ID_DB_NAME = "radix.atom_id_db";
	private static final String VERTEX_STORE_DB_NAME = "radix.vertex_store";
	private static final String ATOMS_DB_NAME = "radix.atom_db";
	private static final String PARTICLE_DB_NAME = "radix.particle_db";
	private static final String UP_PARTICLE_DB_NAME = "radix.up_particle_db";
	private static final String PROOF_DB_NAME = "radix.proof_db";
	private static final String EPOCH_PROOF_DB_NAME = "radix.epoch_proof_db";
	private static final String ATOM_LOG = "radix.ledger";

	private final Serialization serialization;
	private final Hasher hasher;
	private final DatabaseEnvironment dbEnv;
	private final SystemCounters systemCounters;
	private final StoreConfig storeConfig;

	private Database atomDatabase; // Atoms by primary keys (state version, no prefixes); Append-only

	private Database atomIdDatabase; // Atoms by AID; Append-only

	private Database particleDatabase; // Write/Delete
	private SecondaryDatabase upParticleDatabase; // Write/Delete

	private Database vertexStoreDatabase; // Write/Delete

	private Database proofDatabase; // Write/Delete
	private SecondaryDatabase epochProofDatabase;

	private AppendLog atomLog; //Atom data append only log

	@Inject
	public BerkeleyLedgerEntryStore(
		Serialization serialization,
		Hasher hasher,
		DatabaseEnvironment dbEnv,
		StoreConfig storeConfig,
		SystemCounters systemCounters
	) {
		this.serialization = Objects.requireNonNull(serialization);
		this.hasher = Objects.requireNonNull(hasher);
		this.dbEnv = Objects.requireNonNull(dbEnv);
		this.systemCounters = Objects.requireNonNull(systemCounters);
		this.storeConfig = storeConfig;

		this.open();
	}

	@Override
	public void reset() {
		dbEnv.withLock(() -> {
			com.sleepycat.je.Transaction transaction = null;
			try {
				// This SuppressWarnings here is valid, as ownership of the underlying
				// resource is not changed here, the resource is just accessed.
				@SuppressWarnings("resource")
				var env = dbEnv.getEnvironment();
				transaction = env.beginTransaction(null, new TransactionConfig().setReadUncommitted(true));
				env.truncateDatabase(transaction, ATOMS_DB_NAME, false);
				env.truncateDatabase(transaction, ATOM_ID_DB_NAME, false);
				env.truncateDatabase(transaction, PROOF_DB_NAME, false);
				env.truncateDatabase(transaction, EPOCH_PROOF_DB_NAME, false);
				env.truncateDatabase(transaction, VERTEX_STORE_DB_NAME, false);
				transaction.commit();
			} catch (DatabaseNotFoundException e) {
				if (transaction != null) {
					transaction.abort();
				}

				log.warn("Error while resetting database, database not found", e);
			} catch (Exception e) {
				if (transaction != null) {
					transaction.abort();
				}

				throw new BerkeleyStoreException("Error while resetting databases", e);
			}
		});
	}

	@Override
	public void close() {
		safeClose(atomDatabase);

		safeClose(atomIdDatabase);

		safeClose(upParticleDatabase);
		safeClose(particleDatabase);

		safeClose(epochProofDatabase);
		safeClose(proofDatabase);

		safeClose(vertexStoreDatabase);

		if (atomLog != null) {
			atomLog.close();
		}
	}

	@Override
	public boolean contains(AID aid) {
		return withTime(() -> {
			var key = entry(aid.getBytes());
			return SUCCESS == atomIdDatabase.get(null, key, null, DEFAULT);
		}, CounterType.ELAPSED_BDB_LEDGER_CONTAINS, CounterType.COUNT_BDB_LEDGER_CONTAINS);
	}

	@Override
	public Optional<ClientAtom> get(AID aid) {
		return withTime(() -> {
			try {
				var key = entry(aid.getBytes());
				var value = entry();

				if (atomIdDatabase.get(null, key, value, DEFAULT) == SUCCESS) {
					value.setData(atomLog.read(fromByteArray(value.getData())));

					addBytesRead(value, key);
					return Optional.of(deserializeOrElseFail(value.getData(), ClientAtom.class));
				}
			} catch (Exception e) {
				fail("Get of atom '" + aid + "' failed", e);
			}

			return Optional.empty();
		}, CounterType.ELAPSED_BDB_LEDGER_GET, CounterType.COUNT_BDB_LEDGER_GET);
	}

	@Override
	public Transaction createTransaction() {
		return withTime(
			() -> wrap(beginTransaction()),
			CounterType.ELAPSED_BDB_LEDGER_CREATE_TX,
			CounterType.COUNT_BDB_LEDGER_CREATE_TX
		);
	}

	@Override
	public LedgerEntryStoreResult store(
		Transaction tx,
		CommittedAtom atom
	) {
		return withTime(() -> {
			try {
				return doStore(atom, unwrap(tx));
			} catch (Exception e) {
				throw new BerkeleyStoreException("Commit of atom failed", e);
			}
		}, CounterType.ELAPSED_BDB_LEDGER_STORE, CounterType.COUNT_BDB_LEDGER_STORE);
	}

	@Override
	public Optional<SerializedVertexStoreState> loadLastVertexStoreState() {
		return withTime(() -> {
			try (var cursor = vertexStoreDatabase.openCursor(null, null)) {
				var pKey = entry();
				var value = entry();
				var status = cursor.getLast(pKey, value, DEFAULT);

				if (status == SUCCESS) {
					addBytesRead(value, pKey);
					try {
						return Optional.of(serialization.fromDson(value.getData(), SerializedVertexStoreState.class));
					} catch (DeserializeException e) {
						throw new IllegalStateException(e);
					}
				} else {
					return Optional.empty();
				}
			}
		}, CounterType.ELAPSED_BDB_LEDGER_LAST_VERTEX, CounterType.COUNT_BDB_LEDGER_LAST_VERTEX);
	}

	@Override
	public void save(Transaction transaction, VerifiedVertexStoreState vertexStoreState) {
		withTime(
			() -> doSave(transaction.unwrap(), vertexStoreState),
			CounterType.ELAPSED_BDB_LEDGER_SAVE_TX,
			CounterType.COUNT_BDB_LEDGER_SAVE_TX
		);
	}

	@Override
	public void save(VerifiedVertexStoreState vertexStoreState) {
		withTime(() -> {
			var transaction = beginTransaction();
			doSave(transaction, vertexStoreState);
			transaction.commit();
		}, CounterType.ELAPSED_BDB_LEDGER_SAVE, CounterType.COUNT_BDB_LEDGER_SAVE);
	}

	private void open() {
		var primaryConfig = buildPrimaryConfig();
		var pendingConfig = buildPendingConfig();
		var upParticleConfig = buildUpParticleConfig();

		try {
			// This SuppressWarnings here is valid, as ownership of the underlying
			// resource is not changed here, the resource is just accessed.
			@SuppressWarnings("resource")
			var env = dbEnv.getEnvironment();
			atomDatabase = env.openDatabase(null, ATOMS_DB_NAME, primaryConfig);

			particleDatabase = env.openDatabase(null, PARTICLE_DB_NAME, primaryConfig);
			upParticleDatabase = env.openSecondaryDatabase(null, UP_PARTICLE_DB_NAME, particleDatabase, upParticleConfig);

			proofDatabase = env.openDatabase(null, PROOF_DB_NAME, primaryConfig);
			atomIdDatabase = env.openDatabase(null, ATOM_ID_DB_NAME, primaryConfig);
			vertexStoreDatabase = env.openDatabase(null, VERTEX_STORE_DB_NAME, pendingConfig);
			epochProofDatabase = env.openSecondaryDatabase(null, EPOCH_PROOF_DB_NAME, proofDatabase, buildEpochProofConfig());

			atomLog = SimpleAppendLog.openCompressed(new File(env.getHome(), ATOM_LOG).getAbsolutePath(), systemCounters);
		} catch (Exception e) {
			throw new BerkeleyStoreException("Error while opening databases", e);
		}

		if (System.getProperty("db.check_integrity", "1").equals("1")) {
			// TODO implement integrity check
			// TODO perhaps we should implement recovery instead?
		}
	}

	private SecondaryConfig buildUpParticleConfig() {
		return (SecondaryConfig) new SecondaryConfig()
			.setKeyCreator(
				(secondary, key, data, result) -> {
					if (entryToSpin(data) == Spin.DOWN) {
						return false;
					}

					result.setData(data.getData(), 0, EUID.BYTES);
					return true;
				}
			)
			.setSortedDuplicates(true)
			.setAllowCreate(true)
			.setTransactional(true);
	}

	private SecondaryConfig buildEpochProofConfig() {
		return (SecondaryConfig) new SecondaryConfig()
			.setKeyCreator(
				(secondary, key, data, result) -> {
					OptionalLong epoch = headerKeyEpoch(key);
					epoch.ifPresent(e -> result.setData(Longs.toByteArray(e)));
					return epoch.isPresent();
				}
			)
			.setAllowCreate(true)
			.setTransactional(true);
	}

	private DatabaseConfig buildPendingConfig() {
		return new DatabaseConfig()
			.setBtreeComparator(lexicographicalComparator())
			.setAllowCreate(true)
			.setTransactional(true);
	}

	private DatabaseConfig buildPrimaryConfig() {
		return new DatabaseConfig()
			.setAllowCreate(true)
			.setTransactional(true)
			.setKeyPrefixing(true)
			.setBtreeComparator(lexicographicalComparator());
	}

	private static void safeClose(Database database) {
		if (database != null) {
			database.close();
		}
	}

	private static void fail(String message) {
		log.error(message);
		throw new BerkeleyStoreException(message);
	}

	private static void fail(String message, Exception cause) {
		log.error(message, cause);
		throw new BerkeleyStoreException(message, cause);
	}

	private void withTime(Runnable runnable, CounterType elapsed, CounterType count) {
		withTime(
			() -> {
				runnable.run();
				return null;
			},
			elapsed,
			count
		);
	}

	private <T> T withTime(Supplier<T> supplier, CounterType elapsed, CounterType count) {
		final var start = System.nanoTime();
		try {
			return supplier.get();
		} finally {
			addTime(start, elapsed, count);
		}
	}

	private void doSave(com.sleepycat.je.Transaction transaction, VerifiedVertexStoreState vertexStoreState) {
		var rootId = vertexStoreState.getRoot().getId();
		var vertexKey = entry(rootId.asBytes());
		var vertexEntry = serializeAll(vertexStoreState.toSerialized());

		try (var cursor = vertexStoreDatabase.openCursor(transaction, null)) {
			var status = cursor.getLast(null, null, DEFAULT);
			if (status == SUCCESS) {
				cursor.delete();
			}

			this.putNoOverwriteOrElseThrow(
				cursor,
				vertexKey,
				vertexEntry,
				"Store of root vertex with ID " + rootId
			);
		} catch (Exception e) {
			transaction.abort();
			fail("Commit of atom failed", e);
		}
	}

	private static DatabaseEntry toHeaderKey(VerifiedLedgerHeaderAndProof header) {
		if (header.isEndOfEpoch()) {
			return toPKey(header.getStateVersion(), header.getEpoch() + 1);
		} else {
			return toPKey(header.getStateVersion());
		}
	}

	private static OptionalLong headerKeyEpoch(DatabaseEntry entry) {
		if (entry.getData().length == Long.BYTES) {
			return OptionalLong.empty();
		}

		return OptionalLong.of(Longs.fromByteArray(entry.getData(), Long.BYTES));
	}

	private void storeHeader(com.sleepycat.je.Transaction txn, VerifiedLedgerHeaderAndProof header) {
		var prevHeaderKey = entry();
		try (var proofCursor = proofDatabase.openCursor(txn, null);) {
			var status = proofCursor.getLast(prevHeaderKey, null, DEFAULT);
			// Cannot remove end of epoch proofs
			if (status == SUCCESS && headerKeyEpoch(prevHeaderKey).isEmpty()) {
				status = proofCursor.getPrev(prevHeaderKey, null, DEFAULT);
				if (status == SUCCESS) {
					long twoAwayStateVersion = Longs.fromByteArray(prevHeaderKey.getData());
					long versionDiff = header.getStateVersion() - twoAwayStateVersion;
					if (versionDiff <= storeConfig.getMinimumProofBlockSize()) {
						executeOrElseThrow(() -> proofCursor.getNext(null, null, DEFAULT), "Missing next.");
						executeOrElseThrow(proofCursor::delete, "Could not delete header.");
						systemCounters.increment(CounterType.COUNT_BDB_LEDGER_PROOFS_REMOVED);
					}
				}
			}

			final var headerKey = toHeaderKey(header);
			final var headerData = entry(serialize(header));
			this.putNoOverwriteOrElseThrow(
				proofCursor,
				headerKey,
				headerData,
				"Header write failed: " + header,
				CounterType.COUNT_BDB_HEADER_BYTES_WRITE
			);

			systemCounters.increment(CounterType.COUNT_BDB_LEDGER_PROOFS_ADDED);
		}
	}

	public <U extends Particle, V> V reduceUpParticles(
		Class<U> particleClass,
		V initial,
		BiFunction<V, U, V> outputReducer
	) {
		final String idForClass = serialization.getIdForClass(particleClass);
		final EUID numericClassId = SerializationUtils.stringToNumericID(idForClass);
		final byte[] indexableBytes = numericClassId.toByteArray();

		V v = initial;
		try (var particleCursor = upParticleDatabase.openCursor(null, null)) {
			var index = entry(indexableBytes);
			var value = entry();
			var status = particleCursor.getSearchKey(index, null, value, null);
			while (status == SUCCESS) {
				// TODO: Remove memcpy
				byte[] serializedParticle = new byte[value.getData().length - EUID.BYTES];
				System.arraycopy(value.getData(), EUID.BYTES, serializedParticle, 0, serializedParticle.length);
				U particle = deserializeOrElseFail(serializedParticle, particleClass);
				v = outputReducer.apply(v, particle);
				status = particleCursor.getNextDup(index, null, value, null);
			}
		}

		return v;
	}

	private void upParticle(com.sleepycat.je.Transaction txn, Particle particle) {
		HashCode particleId = hasher.hash(particle);
		var particleKey = particleId.asBytes();

		final String idForClass = serialization.getIdForClass(particle.getClass());
		final EUID numericClassId = SerializationUtils.stringToNumericID(idForClass);
		final byte[] indexableBytes = numericClassId.toByteArray();

		byte[] serializedParticle = serialize(particle);
		var value = new byte[EUID.BYTES + serializedParticle.length];
		System.arraycopy(indexableBytes, 0, value, 0, EUID.BYTES);
		System.arraycopy(serializedParticle, 0, value, EUID.BYTES, serializedParticle.length);

		particleDatabase.putNoOverwrite(txn, entry(particleKey), entry(value));
	}


	private void downParticle(com.sleepycat.je.Transaction txn, Particle particle) {
		HashCode particleId = hasher.hash(particle);
		var particleKey = particleId.asBytes();
		// TODO: check for up Particle state
		particleDatabase.put(txn, entry(particleKey), downEntry());
	}

	private DatabaseEntry downEntry() {
		return entry(new byte[0]);
	}

	private Spin entryToSpin(DatabaseEntry e) {
		return e.getData().length == 0 ? Spin.DOWN : Spin.UP;
	}

	private void updateParticle(com.sleepycat.je.Transaction txn, CMMicroInstruction instruction) {
		if (instruction.getMicroOp() == CMMicroInstruction.CMMicroOp.CHECK_NEUTRAL_THEN_UP) {
			upParticle(txn, instruction.getParticle());
		} else if (instruction.getMicroOp() == CMMicroInstruction.CMMicroOp.CHECK_UP_THEN_DOWN) {
			downParticle(txn, instruction.getParticle());
		} else {
			throw new BerkeleyStoreException("Unknown op: " + instruction.getMicroOp());
		}
	}

	private LedgerEntryStoreResult doStore(
		CommittedAtom atom,
		com.sleepycat.je.Transaction transaction
	) {
		var aid = atom.getAID();
		var atomData = serialize(atom.getClientAtom());

		try {
			//Write atom data as soon as possible
			var offset = atomLog.write(atomData);

			// Store atom indices
			var pKey = toPKey(atom.getStateVersion());
			var atomPosData = entry(offset, aid);
			failIfNotSuccess(atomDatabase.putNoOverwrite(transaction, pKey, atomPosData), "Atom write for", aid);
			addBytesWrite(atomPosData, pKey);
			var idKey = entry(aid);
			failIfNotSuccess(atomIdDatabase.put(transaction, idKey, atomPosData), "Atom Id write for", aid);
			addBytesWrite(atomPosData, idKey);

			// Update particles
			atom.getCMInstruction().getMicroInstructions().stream()
				.filter(CMMicroInstruction::isPush)
				.forEach(i -> this.updateParticle(transaction, i));

			// Store header/proof
			atom.getHeaderAndProof().ifPresent(header -> storeHeader(transaction, header));

		} catch (IOException e) {
			return ioFailure(e);
		} catch (UniqueConstraintException e) {
			log.error("Unique indices of ledgerEntry '" + aid + "' are in conflict, aborting transaction");
			if (transaction != null) {
				transaction.abort();
			}
			throw new BerkeleyStoreException("Fatal unique constraint exception", e);
		}
		return success();
	}

	private com.sleepycat.je.Transaction beginTransaction() {
		return dbEnv.getEnvironment().beginTransaction(null, null);
	}

	private <T> byte[] serialize(T instance) {
		return serialization.toDson(instance, Output.PERSIST);
	}

	private <T> DatabaseEntry serializeAll(T instance) {
		return entry(serialization.toDson(instance, Output.ALL));
	}

	private Pair<List<ClientAtom>, VerifiedLedgerHeaderAndProof> getNextCommittedAtomsInternal(long stateVersion) {
		final var start = System.nanoTime();

		com.sleepycat.je.Transaction txn = beginTransaction();
		final VerifiedLedgerHeaderAndProof nextHeader;
		try (var proofCursor = proofDatabase.openCursor(txn, null);) {
			final var headerSearchKey = toPKey(stateVersion + 1);
			final var headerValue = entry();
			var headerCursorStatus = proofCursor.getSearchKeyRange(headerSearchKey, headerValue, DEFAULT);
			if (headerCursorStatus != SUCCESS) {
				return null;
			}
			nextHeader = deserializeOrElseFail(headerValue.getData(), VerifiedLedgerHeaderAndProof.class);
		} finally {
			txn.commit();
		}

		final var atoms = ImmutableList.<ClientAtom>builder();
		final var atomSearchKey = toPKey(stateVersion + 1);
		final var atomPosData = entry();

		try (var atomCursor = atomDatabase.openCursor(null, null)) {
			int atomCount = (int) (nextHeader.getStateVersion() - stateVersion);
			int count = 0;
			var atomCursorStatus = atomCursor.getSearchKeyRange(atomSearchKey, atomPosData, DEFAULT);
			do {
				if (atomCursorStatus != SUCCESS) {
					throw new BerkeleyStoreException("Atom database search failure");
				}
				var offset = fromByteArray(atomPosData.getData());
				var atom = deserializeOrElseFail(atomLog.read(offset), ClientAtom.class);
				atoms.add(atom);
				atomCursorStatus = atomCursor.getNext(atomSearchKey, atomPosData, DEFAULT);
				count++;
			} while (count < atomCount);
			return Pair.of(atoms.build(), nextHeader);
		} catch (IOException e) {
			throw new BerkeleyStoreException("Unable to read from atom store.", e);
		} finally {
			addTime(start, CounterType.ELAPSED_BDB_LEDGER_ENTRIES, CounterType.COUNT_BDB_LEDGER_ENTRIES);
		}
	}

	@Override
	public VerifiedCommandsAndProof getNextCommittedAtoms(long stateVersion) {
		var atoms = this.getNextCommittedAtomsInternal(stateVersion);
		if (atoms == null) {
			return null;
		}
		final var commands = atoms.getFirst().stream()
			.map(a -> new Command(serialization.toDson(a, DsonOutput.Output.PERSIST)))
			.collect(ImmutableList.toImmutableList());
		return new VerifiedCommandsAndProof(commands, atoms.getSecond());
	}

	@Override
	public Spin getSpin(Transaction tx, Particle particle) {
		var particleId = hasher.hash(particle);
		var key = entry(particleId.asBytes());
		var value = entry();
		var status = particleDatabase.get(unwrap(tx), key, value, READ_COMMITTED);
		if (status == SUCCESS) {
			return entryToSpin(value);
		} else {
			return Spin.NEUTRAL;
		}
	}

	@Override
	public Optional<VerifiedLedgerHeaderAndProof> getLastHeader() {
		return withTime(() -> {
			try (var proofCursor = proofDatabase.openCursor(null, null);) {
				var pKey = entry();
				var value = entry();

				return Optional.of(proofCursor.getLast(pKey, value, DEFAULT))
					.filter(status -> status == SUCCESS)
					.map(status -> {
						addBytesRead(value, pKey);
						return deserializeOrElseFail(value.getData(), VerifiedLedgerHeaderAndProof.class);
					});
			}
		}, CounterType.ELAPSED_BDB_LEDGER_LAST_COMMITTED, CounterType.COUNT_BDB_LEDGER_LAST_COMMITTED);
	}

	@Override
	public Optional<VerifiedLedgerHeaderAndProof> getEpochHeader(long epoch) {
		var value = entry();
		var status = epochProofDatabase.get(null, toPKey(epoch), value, null);
		if (status != SUCCESS) {
			return Optional.empty();
		}

		return Optional.of(deserializeOrElseFail(value.getData(), VerifiedLedgerHeaderAndProof.class));
	}


	@Override
	public SearchCursor search() {
		return withTime(() -> {
			try (var databaseCursor = atomDatabase.openCursor(null, null)) {
				var pKey = entry();
				var data = entry();

				if (databaseCursor.getNext(pKey, data, DEFAULT) == SUCCESS) {
					return new BerkeleySearchCursor(this, pKey.getData(), data.getData());
				}

				return null;
			}
		}, CounterType.ELAPSED_BDB_LEDGER_SEARCH, CounterType.COUNT_BDB_LEDGER_SEARCH);
	}


	BerkeleySearchCursor getNext(BerkeleySearchCursor cursor) {
		return withTime(() -> {
			try (var databaseCursor = atomDatabase.openCursor(null, null)) {
				var pKey = entry(cursor.getPrimary());
				var data = entry();

				return Optional.of(databaseCursor.getSearchKeyRange(pKey, data, DEFAULT))
					.filter(status -> status == SUCCESS)
					.map(status -> databaseCursor.getNext(pKey, data, LockMode.DEFAULT))
					.filter(status -> status == SUCCESS)
					.map(status -> new BerkeleySearchCursor(this, pKey.getData(), data.getData()))
					.orElse(null);

			} catch (Exception ex) {
				throw new BerkeleyStoreException("Error while advancing cursor", ex);
			}
		}, CounterType.ELAPSED_BDB_LEDGER_GET_NEXT, CounterType.COUNT_BDB_LEDGER_GET_NEXT);
	}

	private <T> T deserializeOrElseFail(byte[] data, Class<T> c) {
		try {
			return serialization.fromDson(data, c);
		} catch (DeserializeException e) {
			throw new BerkeleyStoreException("Could not deserialize", e);
		}
	}

	private static void failIfNotSuccess(OperationStatus status, String message, Object object) {
		if (status != SUCCESS) {
			fail(message + " '" + object + "' failed with status " + status);
		}
	}

	static DatabaseEntry entry(byte[] data) {
		return new DatabaseEntry(data);
	}

	private static DatabaseEntry entry() {
		return new DatabaseEntry();
	}

	private static DatabaseEntry entry(long offset, AID aid) {
		var value = new byte[Long.BYTES + AID.BYTES];
		Longs.copyTo(offset, value, 0);
		System.arraycopy(aid.getBytes(), 0, value, Long.BYTES, AID.BYTES);
		return entry(value);
	}

	private static DatabaseEntry toPKey(long stateVersion) {
		var pKey = new byte[Long.BYTES];
		Longs.copyTo(stateVersion, pKey, 0);
		return entry(pKey);
	}

	private static DatabaseEntry toPKey(long stateVersion, long epoch) {
		var pKey = new byte[Long.BYTES * 2];
		Longs.copyTo(stateVersion, pKey, 0);
		Longs.copyTo(epoch, pKey, Long.BYTES);
		return entry(pKey);
	}

	private static DatabaseEntry entry(AID aid) {
		return entry(aid.getBytes());
	}

	private void addTime(long start, CounterType detailTime, CounterType detailCounter) {
		final var elapsed = (System.nanoTime() - start + 500L) / 1000L;
		systemCounters.add(CounterType.ELAPSED_BDB_LEDGER_TOTAL, elapsed);
		systemCounters.increment(CounterType.COUNT_BDB_LEDGER_TOTAL);
		systemCounters.add(detailTime, elapsed);
		systemCounters.increment(detailCounter);
	}

	private void addBytesRead(DatabaseEntry entryA, DatabaseEntry entryB) {
		long amount = (long) entryA.getSize() + (long) entryB.getSize();
		systemCounters.add(CounterType.COUNT_BDB_LEDGER_BYTES_READ, amount);
	}

	private void addBytesWrite(DatabaseEntry entryA, DatabaseEntry entryB) {
		long amount = (long) entryA.getSize() + (long) entryB.getSize();
		systemCounters.add(CounterType.COUNT_BDB_LEDGER_BYTES_WRITE, amount);
	}

	private static void executeOrElseThrow(Supplier<OperationStatus> execute, String errorMessage) {
		OperationStatus status = execute.get();
		if (status != SUCCESS) {
			throw new BerkeleyStoreException(errorMessage);
		}
	}

	private void putNoOverwriteOrElseThrow(
		Cursor cursor,
		DatabaseEntry key,
		DatabaseEntry value,
		String errorMessage
	) {
		this.putNoOverwriteOrElseThrow(cursor, key, value, errorMessage, null);
	}

	private void putNoOverwriteOrElseThrow(
		Cursor cursor,
		DatabaseEntry key,
		DatabaseEntry value,
		String errorMessage,
		CounterType additionalCounterType
	) {
		executeOrElseThrow(() -> cursor.putNoOverwrite(key, value), errorMessage);
		long amount = (long) key.getSize() + (long) value.getSize();
		systemCounters.add(CounterType.COUNT_BDB_LEDGER_BYTES_WRITE, amount);
		if (additionalCounterType != null) {
			systemCounters.add(additionalCounterType, amount);
		}
	}

	private static com.sleepycat.je.Transaction unwrap(Transaction tx) {
		return Optional.ofNullable(tx)
			.map(wrapped -> tx.<com.sleepycat.je.Transaction>unwrap())
			.orElse(null);
	}
}
