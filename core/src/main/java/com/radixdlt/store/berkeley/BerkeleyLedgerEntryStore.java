/* Copyright 2021 Radix Publishing Ltd incorporated in Jersey (Channel Islands).
 *
 * Licensed under the Radix License, Version 1.0 (the "License"); you may not use this
 * file except in compliance with the License. You may obtain a copy of the License at:
 *
 * radixfoundation.org/licenses/LICENSE-v1
 *
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

package com.radixdlt.store.berkeley;

import static com.google.common.primitives.UnsignedBytes.lexicographicalComparator;
import static com.radixdlt.utils.Longs.fromByteArray;
import static com.sleepycat.je.LockMode.DEFAULT;
import static com.sleepycat.je.OperationStatus.NOTFOUND;
import static com.sleepycat.je.OperationStatus.SUCCESS;

import com.google.common.base.Stopwatch;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Streams;
import com.google.common.hash.HashCode;
import com.google.common.primitives.Bytes;
import com.google.inject.Inject;
import com.radixdlt.application.system.state.SystemData;
import com.radixdlt.application.system.state.VirtualParent;
import com.radixdlt.application.tokens.state.ResourceData;
import com.radixdlt.application.tokens.state.TokenResource;
import com.radixdlt.application.validators.state.ValidatorData;
import com.radixdlt.atom.CloseableCursor;
import com.radixdlt.atom.SubstateId;
import com.radixdlt.atom.SubstateTypeId;
import com.radixdlt.atom.Txn;
import com.radixdlt.consensus.LedgerProof;
import com.radixdlt.consensus.bft.PersistentVertexStore;
import com.radixdlt.consensus.bft.VerifiedVertexStoreState;
import com.radixdlt.constraintmachine.REOp;
import com.radixdlt.constraintmachine.REProcessedTxn;
import com.radixdlt.constraintmachine.REStateUpdate;
import com.radixdlt.constraintmachine.RawSubstateBytes;
import com.radixdlt.constraintmachine.SubstateIndex;
import com.radixdlt.constraintmachine.SystemMapKey;
import com.radixdlt.constraintmachine.exceptions.VirtualParentStateDoesNotExist;
import com.radixdlt.constraintmachine.exceptions.VirtualSubstateAlreadyDownException;
import com.radixdlt.monitoring.SystemCounters;
import com.radixdlt.monitoring.SystemCounters.CounterType;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.engine.RadixEngineException;
import com.radixdlt.identifiers.AID;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.ledger.DtoLedgerProof;
import com.radixdlt.ledger.VerifiedTxnsAndProof;
import com.radixdlt.serialization.DeserializeException;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.Serialization;
import com.radixdlt.statecomputer.LedgerAndBFTProof;
import com.radixdlt.statecomputer.forks.CandidateForkVote;
import com.radixdlt.statecomputer.forks.ForkConfig;
import com.radixdlt.statecomputer.forks.ForkVotingResult;
import com.radixdlt.statecomputer.forks.ForksEpochStore;
import com.radixdlt.store.DatabaseEnvironment;
import com.radixdlt.store.EngineStore;
import com.radixdlt.store.ResourceStore;
import com.radixdlt.store.StoreConfig;
import com.radixdlt.store.berkeley.atom.AppendLog;
import com.radixdlt.sync.CommittedReader;
import com.radixdlt.utils.Longs;
import com.radixdlt.utils.Shorts;
import com.radixdlt.utils.UInt256;
import com.sleepycat.je.Cursor;
import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.Get;
import com.sleepycat.je.OperationStatus;
import com.sleepycat.je.SecondaryConfig;
import com.sleepycat.je.SecondaryCursor;
import com.sleepycat.je.SecondaryDatabase;
import com.sleepycat.je.Transaction;
import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class BerkeleyLedgerEntryStore
    implements EngineStore<LedgerAndBFTProof>,
        ResourceStore,
        CommittedReader,
        PersistentVertexStore,
        ForksEpochStore {
  private static final Logger log = LogManager.getLogger();

  private final Serialization serialization;
  private final DatabaseEnvironment dbEnv;
  private final SystemCounters systemCounters;
  private final StoreConfig storeConfig;

  // Engine Store databases
  private static final String SUBSTATE_DB_NAME = "radix.substate_db";
  private static final String RESOURCE_DB_NAME = "radix.resource_db";
  private static final String MAP_DB_NAME = "radix.map_db";
  private static final String INDEXED_SUBSTATE_DB_NAME = "radix.indexed_substate_db";
  private Database substatesDatabase; // Write/Delete
  private SecondaryDatabase indexedSubstatesDatabase; // Write/Delete
  private Database resourceDatabase; // Write-only (Resources are immutable)
  private Database mapDatabase;

  // Metadata databases
  private static final String VERTEX_STORE_DB_NAME = "radix.vertex_store";
  private static final String TXN_DB_NAME = "radix.txn_db";
  private static final String FORK_CONFIG_DB = "radix.fork_config_db";
  private static final String FORKS_VOTING_RESULTS_DB = "radix.forks_voting_results";
  // A constant prefix to make any storage format changes easier in the future
  private static final byte[] FORK_VOTING_RESULT_VERSION_PREFIX = {0x01};

  private Database vertexStoreDatabase; // Write/Delete
  private Database proofDatabase; // Write/Delete
  private SecondaryDatabase epochProofDatabase;
  private Database forkConfigDatabase;
  private Database forksVotingResultsDatabase;

  // Syncing Ledger databases
  private static final String PROOF_DB_NAME = "radix.proof_db";
  private static final String EPOCH_PROOF_DB_NAME = "radix.epoch_proof_db";
  private static final String LEDGER_NAME = "radix.ledger";
  private Database txnDatabase; // Txns by state version; Append-only
  private AppendLog txnLog; // Atom data append only log

  private final Set<BerkeleyAdditionalStore> additionalStores;

  @Inject
  public BerkeleyLedgerEntryStore(
      Serialization serialization,
      DatabaseEnvironment dbEnv,
      StoreConfig storeConfig,
      SystemCounters systemCounters,
      Set<BerkeleyAdditionalStore> additionalStores) {
    this.serialization = Objects.requireNonNull(serialization);
    this.dbEnv = Objects.requireNonNull(dbEnv);
    this.systemCounters = Objects.requireNonNull(systemCounters);
    this.storeConfig = storeConfig;
    this.additionalStores = additionalStores;

    this.open();
  }

  public void close() {
    safeClose(txnDatabase);
    safeClose(resourceDatabase);
    safeClose(mapDatabase);

    safeClose(indexedSubstatesDatabase);
    safeClose(substatesDatabase);

    safeClose(epochProofDatabase);
    safeClose(proofDatabase);

    safeClose(vertexStoreDatabase);

    safeClose(forkConfigDatabase);

    safeClose(forksVotingResultsDatabase);

    additionalStores.forEach(BerkeleyAdditionalStore::close);

    if (txnLog != null) {
      txnLog.close();
    }
  }

  private Transaction createTransaction() {
    return withTime(
        () -> beginTransaction(),
        CounterType.ELAPSED_BDB_LEDGER_CREATE_TX,
        CounterType.COUNT_BDB_LEDGER_CREATE_TX);
  }

  @Override
  public <R> R transaction(TransactionEngineStoreConsumer<LedgerAndBFTProof, R> consumer)
      throws RadixEngineException {
    var dbTxn = createTransaction();
    try {
      var result =
          consumer.start(
              new EngineStoreInTransaction<>() {
                @Override
                public void storeTxn(REProcessedTxn txn) {
                  BerkeleyLedgerEntryStore.this.storeTxn(dbTxn, txn);
                }

                @Override
                public void storeMetadata(LedgerAndBFTProof metadata) {
                  BerkeleyLedgerEntryStore.this.storeMetadata(dbTxn, metadata);
                }

                @Override
                public ByteBuffer verifyVirtualSubstate(SubstateId substateId)
                    throws VirtualSubstateAlreadyDownException, VirtualParentStateDoesNotExist {
                  var parent = substateId.getVirtualParent().orElseThrow();

                  var parentState = BerkeleyLedgerEntryStore.this.loadSubstate(dbTxn, parent);
                  if (parentState.isEmpty()) {
                    throw new VirtualParentStateDoesNotExist(parent);
                  }

                  var buf = parentState.get();
                  if (buf.get() != SubstateTypeId.VIRTUAL_PARENT.id()) {
                    throw new VirtualParentStateDoesNotExist(parent);
                  }
                  buf.position(buf.position() - 1);

                  if (BerkeleyLedgerEntryStore.this.isVirtualDown(dbTxn, substateId)) {
                    throw new VirtualSubstateAlreadyDownException(substateId);
                  }

                  return buf;
                }

                @Override
                public Optional<ByteBuffer> loadSubstate(SubstateId substateId) {
                  return BerkeleyLedgerEntryStore.this.loadSubstate(dbTxn, substateId);
                }

                @Override
                public CloseableCursor<RawSubstateBytes> openIndexedCursor(SubstateIndex<?> index) {
                  return BerkeleyLedgerEntryStore.this.openIndexedCursor(dbTxn, index);
                }

                @Override
                public Optional<ByteBuffer> loadResource(REAddr addr) {
                  return BerkeleyLedgerEntryStore.this.loadAddr(dbTxn, addr);
                }
              });
      dbTxn.commit();
      return result;
    } catch (Exception e) {
      dbTxn.abort();
      throw e;
    }
  }

  @Override
  public LedgerAndBFTProof getMetadata() {
    return getLastProof().map(LedgerAndBFTProof::create).orElse(null);
  }

  @Override
  public CloseableCursor<RawSubstateBytes> openIndexedCursor(SubstateIndex<?> index) {
    return BerkeleyLedgerEntryStore.this.openIndexedCursor(null, index);
  }

  private Optional<RawSubstateBytes> getInternal(Transaction dbTxn, SystemMapKey mapKey) {
    var key = new DatabaseEntry(mapKey.array());
    var substateId = new DatabaseEntry();
    var result = mapDatabase.get(dbTxn, key, substateId, null);
    if (result != SUCCESS) {
      return Optional.empty();
    }

    var substate = loadSubstate(dbTxn, SubstateId.fromBytes(substateId.getData())).orElseThrow();
    var substateBytes = new RawSubstateBytes(substateId.getData(), substate.array());
    return Optional.of(substateBytes);
  }

  @Override
  public Optional<RawSubstateBytes> get(SystemMapKey mapKey) {
    return getInternal(null, mapKey);
  }

  private void storeTxn(Transaction dbTxn, REProcessedTxn txn) {
    withTime(
        () -> doStore(dbTxn, txn),
        CounterType.ELAPSED_BDB_LEDGER_STORE,
        CounterType.COUNT_BDB_LEDGER_STORE);
  }

  private void storeMetadata(Transaction dbTxn, LedgerAndBFTProof ledgerAndBFTProof) {
    var proof = ledgerAndBFTProof.getProof();

    try (var atomCursor = txnDatabase.openCursor(dbTxn, null)) {
      var key = entry();
      var status = atomCursor.getLast(key, null, DEFAULT);
      if (status == NOTFOUND) {
        throw new IllegalStateException("No atom found before storing proof.");
      }

      long lastVersion = Longs.fromByteArray(key.getData());
      if (lastVersion != proof.getStateVersion()) {
        throw new IllegalStateException(
            "Proof version "
                + proof.getStateVersion()
                + " does not match last transaction: "
                + lastVersion);
      }
    }

    try (var proofCursor = proofDatabase.openCursor(dbTxn, null)) {
      var prevHeaderKey = entry();
      var status = proofCursor.getLast(prevHeaderKey, null, DEFAULT);
      // Cannot remove end of epoch proofs
      if (status == SUCCESS && headerKeyEpoch(prevHeaderKey).isEmpty()) {
        status = proofCursor.getPrev(prevHeaderKey, null, DEFAULT);
        if (status == SUCCESS) {
          long twoAwayStateVersion = Longs.fromByteArray(prevHeaderKey.getData());
          long versionDiff = proof.getStateVersion() - twoAwayStateVersion;
          if (versionDiff <= storeConfig.getMinimumProofBlockSize()) {
            executeOrElseThrow(() -> proofCursor.getNext(null, null, DEFAULT), "Missing next.");
            executeOrElseThrow(proofCursor::delete, "Could not delete header.");
            systemCounters.increment(CounterType.COUNT_BDB_LEDGER_PROOFS_REMOVED);
          }
        }
      }

      final var headerKey = toHeaderKey(proof);
      final var headerData = entry(serialize(proof));
      this.putNoOverwriteOrElseThrow(
          proofCursor,
          headerKey,
          headerData,
          "Header write failed: " + proof,
          CounterType.COUNT_BDB_HEADER_BYTES_WRITE);

      systemCounters.increment(CounterType.COUNT_BDB_LEDGER_PROOFS_ADDED);
    }

    ledgerAndBFTProof.vertexStoreState().ifPresent(v -> doSave(dbTxn, v));

    final var nextEpoch = ledgerAndBFTProof.getProof().getEpoch() + 1;

    ledgerAndBFTProof
        .getForksVotingResults()
        .ifPresent(forksVotingResults -> storeForksVotingResults(dbTxn, forksVotingResults));

    ledgerAndBFTProof
        .getNextForkName()
        .ifPresent(nextForkName -> this.storeForkAtEpoch(dbTxn, nextEpoch, nextForkName));
  }

  private void storeForksVotingResults(
      Transaction dbTxn, ImmutableSet<ForkVotingResult> forksVotingResults) {
    forksVotingResults.forEach(
        forkVotingResult ->
            forksVotingResultsDatabase.put(
                dbTxn,
                new DatabaseEntry(Longs.toByteArray(forkVotingResult.epoch())),
                new DatabaseEntry(encodeForkVotingResult(forkVotingResult))));
  }

  @Override
  public CloseableCursor<ForkVotingResult> forkVotingResultsCursor(
      long fromEpoch, long toEpoch, HashCode candidateForkId) {
    final Cursor underlyingCursor = forksVotingResultsDatabase.openCursor(null, null);

    OperationStatus cursorStatus;
    try {
      cursorStatus =
          underlyingCursor.getSearchKeyRange(
              new DatabaseEntry(Longs.toByteArray(fromEpoch)), null, DEFAULT);
    } catch (Exception e) {
      // close the cursor and re-throw
      underlyingCursor.close();
      throw e;
    }

    if (cursorStatus != SUCCESS) {
      underlyingCursor.close();
      return CloseableCursor.empty();
    }

    return new CloseableCursor<>() {
      private Optional<ForkVotingResult> nextOpt =
          findNextForkVotingResult(toEpoch, candidateForkId, underlyingCursor, true);

      @Override
      public boolean hasNext() {
        return nextOpt.isPresent();
      }

      @Override
      public ForkVotingResult next() {
        final var res = nextOpt.orElseThrow(NoSuchElementException::new);
        nextOpt = findNextForkVotingResult(toEpoch, candidateForkId, underlyingCursor, false);
        return res;
      }

      @Override
      public void close() {
        underlyingCursor.close();
      }
    };
  }

  private Optional<ForkVotingResult> findNextForkVotingResult(
      long toEpoch, HashCode candidateForkId, Cursor cursor, boolean startWithCurrent) {
    final DatabaseEntry key = new DatabaseEntry();
    final DatabaseEntry value = new DatabaseEntry();
    OperationStatus operationStatus =
        startWithCurrent
            ? cursor.getCurrent(key, value, DEFAULT)
            : cursor.getNext(key, value, DEFAULT);

    if (operationStatus != SUCCESS) {
      return Optional.empty();
    }

    long epoch = Longs.fromByteArray(key.getData());

    while (operationStatus == SUCCESS && epoch < toEpoch) {
      final var nextForkVotingResult = decodeForkVotingResult(epoch, value.getData());
      if (nextForkVotingResult.candidateForkId().equals(candidateForkId)) {
        return Optional.of(nextForkVotingResult);
      }
      operationStatus = cursor.getNext(key, value, DEFAULT);
      if (operationStatus == SUCCESS) {
        epoch = Longs.fromByteArray(key.getData());
      }
    }

    return Optional.empty();
  }

  private byte[] encodeForkVotingResult(ForkVotingResult forkVotingResult) {
    return Bytes.concat(
        FORK_VOTING_RESULT_VERSION_PREFIX,
        forkVotingResult.candidateForkId().asBytes(),
        Shorts.toByteArray(forkVotingResult.stakePercentageVoted()));
  }

  private ForkVotingResult decodeForkVotingResult(long epoch, byte[] data) {
    final var candidateForkId =
        HashCode.fromBytes(
            Arrays.copyOfRange(
                data,
                FORK_VOTING_RESULT_VERSION_PREFIX.length,
                CandidateForkVote.CANDIDATE_FORK_ID_LEN
                    + FORK_VOTING_RESULT_VERSION_PREFIX.length));
    final var stakePercentageVoted =
        Shorts.fromByteArray(
            Arrays.copyOfRange(
                data,
                FORK_VOTING_RESULT_VERSION_PREFIX.length + CandidateForkVote.CANDIDATE_FORK_ID_LEN,
                data.length));
    return new ForkVotingResult(epoch, candidateForkId, stakePercentageVoted);
  }

  private void storeForkAtEpoch(Transaction dbTxn, long newEpoch, String forkName) {
    final var key = new DatabaseEntry(Longs.toByteArray(newEpoch));
    final var entry = new DatabaseEntry(forkName.getBytes(ForkConfig.FORK_NAME_CHARSET));
    if (forkConfigDatabase.putNoOverwrite(dbTxn, key, entry) != SUCCESS) {
      throw new BerkeleyStoreException("Duplicate fork hash stored for epoch " + newEpoch);
    }
  }

  @Override
  public ImmutableSet<ForkVotingResult> getForksVotingResultsForEpoch(long epoch) {
    final var result = new ImmutableSet.Builder<ForkVotingResult>();
    final var value = new DatabaseEntry();
    try (final var cursor = forksVotingResultsDatabase.openCursor(null, null)) {
      OperationStatus status =
          cursor.getSearchKey(new DatabaseEntry(Longs.toByteArray(epoch)), value, DEFAULT);
      while (status == SUCCESS) {
        result.add(decodeForkVotingResult(epoch, value.getData()));
        status = cursor.getNextDup(null, value, DEFAULT);
      }
    }
    return result.build();
  }

  @Override
  public void storeForkAtEpoch(long newEpoch, String forkName) {
    final var tx = beginTransaction();
    try {
      storeForkAtEpoch(tx, newEpoch, forkName);
      tx.commit();
    } catch (Exception e) {
      log.error("Failed to store fork {} at epoch {}", forkName, newEpoch);
      tx.abort();
      throw new RuntimeException(e);
    }
  }

  @Override
  public ImmutableMap<Long, String> getStoredForks() {
    final var builder = ImmutableMap.<Long, String>builder();
    try (var cursor = forkConfigDatabase.openCursor(null, null)) {
      var key = entry();
      var value = entry();
      while (cursor.getNext(key, value, DEFAULT) == OperationStatus.SUCCESS) {
        builder.put(
            Longs.fromByteArray(key.getData()),
            new String(value.getData(), ForkConfig.FORK_NAME_CHARSET));
      }
    }
    return builder.build();
  }

  public Optional<SerializedVertexStoreState> loadLastVertexStoreState() {
    return withTime(
        () -> {
          try (var cursor = vertexStoreDatabase.openCursor(null, null)) {
            var pKey = entry();
            var value = entry();
            var status = cursor.getLast(pKey, value, DEFAULT);

            if (status == SUCCESS) {
              addBytesRead(value, pKey);
              try {
                return Optional.of(
                    serialization.fromDson(value.getData(), SerializedVertexStoreState.class));
              } catch (DeserializeException e) {
                throw new IllegalStateException(e);
              }
            } else {
              return Optional.empty();
            }
          }
        },
        CounterType.ELAPSED_BDB_LEDGER_LAST_VERTEX,
        CounterType.COUNT_BDB_LEDGER_LAST_VERTEX);
  }

  @Override
  public void save(VerifiedVertexStoreState vertexStoreState) {
    withTime(
        () -> {
          var transaction = beginTransaction();
          doSave(transaction, vertexStoreState);
          transaction.commit();
        },
        CounterType.ELAPSED_BDB_LEDGER_SAVE,
        CounterType.COUNT_BDB_LEDGER_SAVE);
  }

  private void open() {
    var primaryConfig = buildPrimaryConfig();
    var rriConfig = buildRriConfig();
    var pendingConfig = buildPendingConfig();

    try {
      // This SuppressWarnings here is valid, as ownership of the underlying
      // resource is not changed here, the resource is just accessed.
      @SuppressWarnings("resource")
      var env = dbEnv.getEnvironment();
      txnDatabase = env.openDatabase(null, TXN_DB_NAME, primaryConfig);

      resourceDatabase = env.openDatabase(null, RESOURCE_DB_NAME, rriConfig);
      mapDatabase = env.openDatabase(null, MAP_DB_NAME, rriConfig);
      substatesDatabase = env.openDatabase(null, SUBSTATE_DB_NAME, primaryConfig);

      indexedSubstatesDatabase =
          env.openSecondaryDatabase(
              null,
              INDEXED_SUBSTATE_DB_NAME,
              substatesDatabase,
              (SecondaryConfig)
                  new SecondaryConfig()
                      .setKeyCreator(
                          (secondary, key, data, result) -> {
                            if (entryToSpin(data) != REOp.UP) {
                              return false;
                            }

                            var substateTypeId = data.getData()[data.getOffset()];
                            final int prefixIndexSize;
                            if (substateTypeId == SubstateTypeId.TOKENS.id()) {
                              // Indexing not necessary for verification at the moment but useful
                              // for construction

                              // 0: Type Byte
                              // 1: Reserved Byte
                              // 2-37: Account Address
                              prefixIndexSize = 2 + (1 + ECPublicKey.COMPRESSED_BYTES);
                            } else if (substateTypeId == SubstateTypeId.STAKE_OWNERSHIP.id()) {
                              // Indexing not necessary for verification at the moment but useful
                              // for construction

                              // This should have had validator keys and account addresses switched
                              // so that
                              // prefix indexing could be done against account addresses rather than
                              // validators
                              // so that actions like "Unstake Everything" could be implemented and
                              // queries against
                              // accounts. A later to do...

                              // 0: Type Byte
                              // 1: Reserved Byte
                              // 2-36: Validator Key
                              // 37-69: Account Address
                              prefixIndexSize =
                                  2
                                      + ECPublicKey.COMPRESSED_BYTES
                                      + (1 + ECPublicKey.COMPRESSED_BYTES);
                            } else if (substateTypeId == SubstateTypeId.EXITING_STAKE.id()) {
                              // 0: Type Byte
                              // 1: Reserved Byte
                              // 2-5: Epoch
                              // 6-40: Validator Key
                              // 41-73: Account Address
                              prefixIndexSize =
                                  2
                                      + Long.BYTES
                                      + ECPublicKey.COMPRESSED_BYTES
                                      + (1 + ECPublicKey.COMPRESSED_BYTES);
                            } else if (substateTypeId == SubstateTypeId.PREPARED_STAKE.id()) {
                              // 0: Type Byte
                              // 1: Reserved Byte
                              // 2-36: Validator Key
                              // 37-69: Account Address
                              prefixIndexSize =
                                  2
                                      + ECPublicKey.COMPRESSED_BYTES
                                      + (1 + ECPublicKey.COMPRESSED_BYTES);
                            } else if (substateTypeId == SubstateTypeId.PREPARED_UNSTAKE.id()) {
                              // 0: Type Byte
                              // 1: Reserved Byte
                              // 2-36: Validator Key
                              // 37-69: Account Address
                              prefixIndexSize =
                                  2
                                      + ECPublicKey.COMPRESSED_BYTES
                                      + (1 + ECPublicKey.COMPRESSED_BYTES);
                            } else if (substateTypeId == SubstateTypeId.VALIDATOR_OWNER_COPY.id()) {
                              // 0: Type Byte
                              // 1: Reserved Byte
                              // 2: Optional flag
                              // 3-6: Epoch
                              // 7-41: Validator Key
                              prefixIndexSize = 3 + Long.BYTES + ECPublicKey.COMPRESSED_BYTES;
                            } else if (substateTypeId
                                == SubstateTypeId.VALIDATOR_REGISTERED_FLAG_COPY.id()) {
                              // 0: Type Byte
                              // 1: Reserved Byte
                              // 2: Optional flag
                              // 3-6: Epoch
                              // 7-41: Validator Key
                              prefixIndexSize = 3 + Long.BYTES + ECPublicKey.COMPRESSED_BYTES;
                            } else if (substateTypeId == SubstateTypeId.VALIDATOR_RAKE_COPY.id()) {
                              // 0: Type Byte
                              // 1: Reserved Byte
                              // 2: Optional flag
                              // 3-6: Epoch
                              // 7-41: Validator Key
                              prefixIndexSize = 3 + Long.BYTES + ECPublicKey.COMPRESSED_BYTES;
                            } else if (substateTypeId == SubstateTypeId.VALIDATOR_STAKE_DATA.id()) {
                              // 0: Type Byte
                              // 1: Reserved Byte
                              // 2: Registered Byte
                              // 3-34: Stake amount
                              // 35-67: Validator key
                              prefixIndexSize = 3 + UInt256.BYTES + ECPublicKey.COMPRESSED_BYTES;
                            } else {
                              // 0: Type Byte
                              prefixIndexSize = 1;
                            }
                            // Index by substate type
                            result.setData(data.getData(), data.getOffset(), prefixIndexSize);
                            return true;
                          })
                      .setBtreeComparator(lexicographicalComparator())
                      .setSortedDuplicates(true)
                      .setAllowCreate(true)
                      .setTransactional(true));

      proofDatabase = env.openDatabase(null, PROOF_DB_NAME, primaryConfig);
      vertexStoreDatabase = env.openDatabase(null, VERTEX_STORE_DB_NAME, pendingConfig);
      epochProofDatabase =
          env.openSecondaryDatabase(
              null, EPOCH_PROOF_DB_NAME, proofDatabase, buildEpochProofConfig());

      forkConfigDatabase = env.openDatabase(null, FORK_CONFIG_DB, primaryConfig);
      forksVotingResultsDatabase =
          env.openDatabase(
              null, FORKS_VOTING_RESULTS_DB, primaryConfig.clone().setSortedDuplicates(true));

      txnLog =
          AppendLog.openCompressed(
              new File(env.getHome(), LEDGER_NAME).getAbsolutePath(), systemCounters);
    } catch (Exception e) {
      throw new BerkeleyStoreException("Error while opening databases", e);
    }

    this.additionalStores.forEach(b -> b.open(dbEnv));

    if (System.getProperty("db.check_integrity", "1").equals("1")) {
      // TODO implement integrity check
      // TODO perhaps we should implement recovery instead?
      // TODO recovering should be integrated with recovering of ClientApiStore
    }
  }

  private SecondaryConfig buildEpochProofConfig() {
    return (SecondaryConfig)
        new SecondaryConfig()
            .setKeyCreator(
                (secondary, key, data, result) -> {
                  OptionalLong epoch = headerKeyEpoch(key);
                  epoch.ifPresent(e -> result.setData(Longs.toByteArray(e)));
                  return epoch.isPresent();
                })
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

  private DatabaseConfig buildRriConfig() {
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
        count);
  }

  private <T> T withTime(Supplier<T> supplier, CounterType elapsed, CounterType count) {
    final var start = System.nanoTime();
    try {
      return supplier.get();
    } finally {
      addTime(start, elapsed, count);
    }
  }

  private void doSave(
      com.sleepycat.je.Transaction transaction, VerifiedVertexStoreState vertexStoreState) {
    var rootId = vertexStoreState.getRoot().getId();
    var vertexKey = entry(rootId.asBytes());
    var vertexEntry = serializeAll(vertexStoreState.toSerialized());

    try (var cursor = vertexStoreDatabase.openCursor(transaction, null)) {
      var status = cursor.getLast(null, null, DEFAULT);
      if (status == SUCCESS) {
        cursor.delete();
      }

      this.putNoOverwriteOrElseThrow(
          cursor, vertexKey, vertexEntry, "Store of root vertex with ID " + rootId);
    } catch (Exception e) {
      transaction.abort();
      fail("Commit of atom failed", e);
    }
  }

  private static DatabaseEntry toHeaderKey(LedgerProof header) {
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

  private static class BerkeleySubstateCursor implements CloseableCursor<RawSubstateBytes> {
    private final SecondaryDatabase db;
    private final com.sleepycat.je.Transaction dbTxn;
    private final byte[] indexableBytes;
    private final boolean reverse;
    private SecondaryCursor cursor;
    private OperationStatus status;

    private DatabaseEntry key;
    private DatabaseEntry value = entry();
    private DatabaseEntry substateIdBytes = entry();

    BerkeleySubstateCursor(
        com.sleepycat.je.Transaction dbTxn, SecondaryDatabase db, byte[] indexableBytes) {
      this.dbTxn = dbTxn;
      this.db = db;
      this.indexableBytes = indexableBytes;
      this.reverse =
          indexableBytes[0] == SubstateTypeId.VALIDATOR_STAKE_DATA.id()
              || indexableBytes[0] == SubstateTypeId.VALIDATOR_RAKE_COPY.id()
              || indexableBytes[0] == SubstateTypeId.VALIDATOR_OWNER_COPY.id()
              || indexableBytes[0] == SubstateTypeId.VALIDATOR_REGISTERED_FLAG_COPY.id();
    }

    private void open() {
      this.cursor = db.openCursor(dbTxn, null);
      if (reverse) {
        if ((indexableBytes[0] & 0x80) != 0) {
          throw new IllegalStateException("Unexpected first byte.");
        }
        var copy = new BigInteger(indexableBytes);
        var firstKey = copy.add(BigInteger.ONE).toByteArray();
        this.key = entry(firstKey);
        cursor.getSearchKeyRange(key, substateIdBytes, value, null);
        this.status = cursor.getPrev(key, substateIdBytes, value, null);
      } else {
        this.key = entry(indexableBytes);
        this.status = cursor.getSearchKeyRange(key, substateIdBytes, value, null);
      }
    }

    @Override
    public void close() {
      if (cursor != null) {
        cursor.close();
      }
    }

    @Override
    public boolean hasNext() {
      if (status != SUCCESS) {
        return false;
      }

      if (indexableBytes.length > key.getData().length) {
        return false;
      }

      return Arrays.equals(
          indexableBytes, 0, indexableBytes.length, key.getData(), 0, indexableBytes.length);
    }

    @Override
    public RawSubstateBytes next() {
      if (status != SUCCESS) {
        throw new NoSuchElementException();
      }

      var next = new RawSubstateBytes(substateIdBytes.getData(), value.getData());
      if (reverse) {
        status = cursor.getPrev(key, substateIdBytes, value, null);
      } else {
        status = cursor.getNext(key, substateIdBytes, value, null);
      }
      return next;
    }
  }

  private CloseableCursor<RawSubstateBytes> openIndexedCursor(
      Transaction dbTxn, SubstateIndex<?> index) {
    var cursor = new BerkeleySubstateCursor(dbTxn, indexedSubstatesDatabase, index.getPrefix());
    cursor.open();
    return cursor;
  }

  private void upParticle(
      com.sleepycat.je.Transaction txn, ByteBuffer bytes, SubstateId substateId) {
    byte[] particleKey = substateId.asBytes();
    var value = new DatabaseEntry(bytes.array(), bytes.position(), bytes.remaining());
    substatesDatabase.putNoOverwrite(txn, entry(particleKey), value);
  }

  private void downVirtualSubstate(com.sleepycat.je.Transaction txn, SubstateId substateId) {
    var particleKey = substateId.asBytes();
    substatesDatabase.putNoOverwrite(txn, entry(particleKey), downEntry());
  }

  private void downSubstate(com.sleepycat.je.Transaction txn, SubstateId substateId) {
    var status = substatesDatabase.delete(txn, entry(substateId.asBytes()));
    if (status != SUCCESS) {
      throw new IllegalStateException("Downing particle does not exist " + substateId);
    }
  }

  private DatabaseEntry downEntry() {
    return entry(new byte[0]);
  }

  private REOp entryToSpin(DatabaseEntry e) {
    return e.getData().length == 0 ? REOp.DOWN : REOp.UP;
  }

  private Optional<ByteBuffer> entryToSubstate(DatabaseEntry e) {
    if (entryToSpin(e) == REOp.DOWN) {
      return Optional.empty();
    }
    return Optional.of(ByteBuffer.wrap(e.getData()));
  }

  private void insertIntoMapDatabaseOrFail(
      com.sleepycat.je.Transaction txn, SystemMapKey mapKey, SubstateId substateId) {
    var key = new DatabaseEntry(mapKey.array());
    var value = new DatabaseEntry(substateId.asBytes());
    var result = mapDatabase.putNoOverwrite(txn, key, value);
    if (result != SUCCESS) {
      throw new IllegalStateException("Unable to insert into map database");
    }
  }

  private void deleteFromMapDatabaseOrFail(com.sleepycat.je.Transaction txn, SystemMapKey mapKey) {
    var key = new DatabaseEntry(mapKey.array());
    var result = mapDatabase.delete(txn, key);
    if (result != SUCCESS) {
      throw new IllegalStateException("Unable to delete from map database");
    }
  }

  private void executeStateUpdate(com.sleepycat.je.Transaction txn, REStateUpdate stateUpdate) {
    if (stateUpdate.isBootUp()) {
      var buf = stateUpdate.getStateBuf();
      upParticle(txn, buf, stateUpdate.getId());

      // FIXME: Superhack
      if (stateUpdate.getParsed() instanceof TokenResource) {
        var p = (TokenResource) stateUpdate.getParsed();
        var addr = p.addr();
        var buf2 = stateUpdate.getStateBuf();
        var value = new DatabaseEntry(buf2.array(), buf2.position(), buf2.remaining());
        resourceDatabase.putNoOverwrite(txn, new DatabaseEntry(addr.getBytes()), value);
      }

      // TODO: The following is not required for verification. Only useful for construction
      // TODO: and stateful reads, move this into a separate store at some point.
      if (stateUpdate.getParsed() instanceof VirtualParent) {
        var p = (VirtualParent) stateUpdate.getParsed();
        var typeByte = p.data()[0];
        var mapKey = SystemMapKey.ofSystem(typeByte);
        insertIntoMapDatabaseOrFail(txn, mapKey, stateUpdate.getId());
      } else if (stateUpdate.getParsed() instanceof ResourceData) {
        var p = (ResourceData) stateUpdate.getParsed();
        var mapKey = SystemMapKey.ofResourceData(p.addr(), stateUpdate.typeByte());
        insertIntoMapDatabaseOrFail(txn, mapKey, stateUpdate.getId());
      } else if (stateUpdate.getParsed() instanceof ValidatorData) {
        var p = (ValidatorData) stateUpdate.getParsed();
        var mapKey =
            SystemMapKey.ofSystem(stateUpdate.typeByte(), p.validatorKey().getCompressedBytes());
        insertIntoMapDatabaseOrFail(txn, mapKey, stateUpdate.getId());
      } else if (stateUpdate.getParsed() instanceof SystemData) {
        var mapKey = SystemMapKey.ofSystem(stateUpdate.typeByte());
        insertIntoMapDatabaseOrFail(txn, mapKey, stateUpdate.getId());
      }
    } else if (stateUpdate.isShutDown()) {
      if (stateUpdate.getId().isVirtual()) {
        downVirtualSubstate(txn, stateUpdate.getId());
      } else {
        downSubstate(txn, stateUpdate.getId());

        if (stateUpdate.getParsed() instanceof ResourceData) {
          var p = (ResourceData) stateUpdate.getParsed();
          var mapKey = SystemMapKey.ofResourceData(p.addr(), stateUpdate.typeByte());
          deleteFromMapDatabaseOrFail(txn, mapKey);
        } else if (stateUpdate.getParsed() instanceof ValidatorData) {
          var p = (ValidatorData) stateUpdate.getParsed();
          var mapKey =
              SystemMapKey.ofSystem(stateUpdate.typeByte(), p.validatorKey().getCompressedBytes());
          deleteFromMapDatabaseOrFail(txn, mapKey);
        } else if (stateUpdate.getParsed() instanceof SystemData) {
          var mapKey = SystemMapKey.ofSystem(stateUpdate.typeByte());
          deleteFromMapDatabaseOrFail(txn, mapKey);
        }
      }
    } else {
      throw new IllegalStateException("Must bootup or shutdown to update particle: " + stateUpdate);
    }
  }

  private void doStore(Transaction dbTxn, REProcessedTxn txn) {
    final long stateVersion;
    final long expectedOffset;
    try (var cursor = txnDatabase.openCursor(dbTxn, null)) {
      var key = entry();
      var data = entry();
      var status = cursor.getLast(key, data, DEFAULT);
      if (status == OperationStatus.NOTFOUND) {
        stateVersion = 1;
        expectedOffset = 0;
      } else {
        stateVersion = Longs.fromByteArray(key.getData()) + 1;
        long prevOffset = Longs.fromByteArray(data.getData());
        long prevSize = Longs.fromByteArray(data.getData(), Long.BYTES);
        expectedOffset = prevOffset + prevSize;
      }
    }

    try {
      // Transaction / Syncing database
      var aid = txn.getTxn().getId();
      // Write atom data as soon as possible
      var storedSize = txnLog.write(txn.getTxn().getPayload(), expectedOffset);
      // Store atom indices
      var pKey = toPKey(stateVersion);
      var atomPosData = txnEntry(expectedOffset, storedSize, aid);
      failIfNotSuccess(txnDatabase.putNoOverwrite(dbTxn, pKey, atomPosData), "Atom write for", aid);
      addBytesWrite(atomPosData, pKey);
      systemCounters.increment(CounterType.COUNT_BDB_LEDGER_COMMIT);

      // State database
      var elapsed = Stopwatch.createStarted();
      int totalCount =
          txn.getGroupedStateUpdates().stream().mapToInt(List::size).reduce(Integer::sum).orElse(0);
      int count = 0;
      for (var group : txn.getGroupedStateUpdates()) {
        for (var stateUpdate : group) {
          if (count > 0 && count % 100000 == 0) {
            log.warn(
                "engine_store large_state_update: {}/{} elapsed_time={}s",
                count,
                totalCount,
                elapsed.elapsed(TimeUnit.SECONDS));
          }
          try {
            this.executeStateUpdate(dbTxn, stateUpdate);
            count++;
          } catch (Exception e) {
            if (dbTxn != null) {
              dbTxn.abort();
            }
            throw new BerkeleyStoreException(
                "Unable to store transaction, failed on stateUpdate " + count + ": " + stateUpdate,
                e);
          }
        }
      }

      additionalStores.forEach(
          b -> b.process(dbTxn, txn, stateVersion, k -> getInternal(dbTxn, k)));

    } catch (Exception e) {
      if (dbTxn != null) {
        dbTxn.abort();
      }
      throw new BerkeleyStoreException("Unable to store atom:\n" + txn, e);
    }
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

  @Override
  public VerifiedTxnsAndProof getNextCommittedTxns(DtoLedgerProof start) {

    long stateVersion = start.getLedgerHeader().getAccumulatorState().getStateVersion();
    final var startTime = System.nanoTime();

    com.sleepycat.je.Transaction txn = beginTransaction();
    final LedgerProof nextHeader;
    try (var proofCursor = proofDatabase.openCursor(txn, null)) {
      final var headerSearchKey = toPKey(stateVersion + 1);
      final var headerValue = entry();
      var headerCursorStatus = proofCursor.getSearchKeyRange(headerSearchKey, headerValue, DEFAULT);
      if (headerCursorStatus != SUCCESS) {
        return null;
      }
      nextHeader = deserializeOrElseFail(headerValue.getData(), LedgerProof.class);
    } finally {
      txn.commit();
    }

    final var txns = ImmutableList.<Txn>builder();
    final var atomSearchKey = toPKey(stateVersion + 1);
    final var atomPosData = entry();

    try (var txnCursor = txnDatabase.openCursor(null, null)) {
      int atomCount = (int) (nextHeader.getStateVersion() - stateVersion);
      int count = 0;
      var atomCursorStatus = txnCursor.getSearchKeyRange(atomSearchKey, atomPosData, DEFAULT);
      do {
        if (atomCursorStatus != SUCCESS) {
          throw new BerkeleyStoreException("Atom database search failure");
        }
        var offset = fromByteArray(atomPosData.getData());
        var txnBytes = txnLog.read(offset);
        txns.add(Txn.create(txnBytes));
        atomCursorStatus = txnCursor.getNext(atomSearchKey, atomPosData, DEFAULT);
        count++;
      } while (count < atomCount);

      return VerifiedTxnsAndProof.create(txns.build(), nextHeader);
    } catch (IOException e) {
      throw new BerkeleyStoreException("Unable to read from atom store.", e);
    } finally {
      addTime(
          startTime, CounterType.ELAPSED_BDB_LEDGER_ENTRIES, CounterType.COUNT_BDB_LEDGER_ENTRIES);
    }
  }

  public List<Txn> getCommittedTxns(long stateVersion, long limit) {
    try (var txnCursor = txnDatabase.openCursor(null, null)) {
      var iterator =
          new Iterator<Txn>() {
            final DatabaseEntry key = new DatabaseEntry(Longs.toByteArray(stateVersion + 1));
            final DatabaseEntry value = new DatabaseEntry();
            OperationStatus status =
                txnCursor.get(key, value, Get.SEARCH, null) != null
                    ? SUCCESS
                    : OperationStatus.NOTFOUND;

            @Override
            public boolean hasNext() {
              return status == SUCCESS;
            }

            @Override
            public Txn next() {
              if (status != SUCCESS) {
                throw new NoSuchElementException();
              }
              var offset = fromByteArray(value.getData());
              byte[] txnBytes;
              try {
                txnBytes = txnLog.read(offset);
              } catch (IOException e) {
                throw new IllegalStateException("Unable to read transaction", e);
              }
              Txn next = Txn.create(txnBytes);

              status = txnCursor.getNext(key, value, null);
              return next;
            }
          };
      return Streams.stream(iterator).limit(limit).onClose(txnCursor::close).toList();
    }
  }

  @Override
  public Optional<ByteBuffer> loadResource(REAddr addr) {
    return loadAddr(null, addr);
  }

  private Optional<ByteBuffer> loadAddr(Transaction dbTxn, REAddr addr) {
    var buf = ByteBuffer.allocate(128);
    buf.put(addr.getBytes());
    var pos = buf.position();
    var key = new DatabaseEntry(buf.array(), 0, pos);
    var value = entry();
    var status = resourceDatabase.get(dbTxn, key, value, DEFAULT);
    if (status != SUCCESS) {
      return Optional.empty();
    }

    return entryToSubstate(value);
  }

  private boolean isVirtualDown(Transaction dbTxn, SubstateId substateId) {
    var key = entry(substateId.asBytes());
    var value = entry();
    var status = substatesDatabase.get(dbTxn, key, value, DEFAULT);
    return status == SUCCESS;
  }

  private Optional<ByteBuffer> loadSubstate(Transaction dbTxn, SubstateId substateId) {
    var key = entry(substateId.asBytes());
    var value = entry();
    var status = substatesDatabase.get(dbTxn, key, value, DEFAULT);
    if (status != SUCCESS) {
      return Optional.empty();
    }

    return entryToSubstate(value);
  }

  @Override
  public Optional<LedgerProof> getLastProof() {
    return withTime(
        () -> {
          try (var proofCursor = proofDatabase.openCursor(null, null)) {
            var pKey = entry();
            var value = entry();

            return Optional.of(proofCursor.getLast(pKey, value, DEFAULT))
                .filter(status -> status == SUCCESS)
                .map(
                    status -> {
                      addBytesRead(value, pKey);
                      return deserializeOrElseFail(value.getData(), LedgerProof.class);
                    });
          }
        },
        CounterType.ELAPSED_BDB_LEDGER_LAST_COMMITTED,
        CounterType.COUNT_BDB_LEDGER_LAST_COMMITTED);
  }

  @Override
  public Optional<LedgerProof> getEpochProof(long epoch) {
    var value = entry();
    var status = epochProofDatabase.get(null, toPKey(epoch), value, null);
    if (status != SUCCESS) {
      return Optional.empty();
    }

    return Optional.of(deserializeOrElseFail(value.getData(), LedgerProof.class));
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

  private static DatabaseEntry txnEntry(long offset, long size, AID aid) {
    var buf = ByteBuffer.allocate(Long.BYTES + Long.BYTES + AID.BYTES);
    buf.putLong(offset);
    buf.putLong(size);
    buf.put(aid.getBytes());
    return entry(buf.array());
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
      Cursor cursor, DatabaseEntry key, DatabaseEntry value, String errorMessage) {
    this.putNoOverwriteOrElseThrow(cursor, key, value, errorMessage, null);
  }

  private void putNoOverwriteOrElseThrow(
      Cursor cursor,
      DatabaseEntry key,
      DatabaseEntry value,
      String errorMessage,
      CounterType additionalCounterType) {
    executeOrElseThrow(() -> cursor.putNoOverwrite(key, value), errorMessage);
    long amount = (long) key.getSize() + (long) value.getSize();
    systemCounters.add(CounterType.COUNT_BDB_LEDGER_BYTES_WRITE, amount);
    if (additionalCounterType != null) {
      systemCounters.add(additionalCounterType, amount);
    }
  }
}
