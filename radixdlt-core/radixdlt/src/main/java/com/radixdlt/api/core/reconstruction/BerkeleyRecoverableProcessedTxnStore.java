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

package com.radixdlt.api.core.reconstruction;

import static com.google.common.primitives.UnsignedBytes.lexicographicalComparator;
import static com.sleepycat.je.OperationStatus.SUCCESS;

import com.google.common.collect.Streams;
import com.google.common.hash.HashCode;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.radixdlt.application.system.state.RoundData;
import com.radixdlt.constraintmachine.REProcessedTxn;
import com.radixdlt.constraintmachine.RawSubstateBytes;
import com.radixdlt.constraintmachine.SystemMapKey;
import com.radixdlt.crypto.HashUtils;
import com.radixdlt.engine.RadixEngine;
import com.radixdlt.ledger.AccumulatorState;
import com.radixdlt.ledger.LedgerAccumulator;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.Serialization;
import com.radixdlt.statecomputer.LedgerAndBFTProof;
import com.radixdlt.store.DatabaseEnvironment;
import com.radixdlt.store.berkeley.BerkeleyAdditionalStore;
import com.radixdlt.utils.Compress;
import com.radixdlt.utils.Longs;
import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.Get;
import com.sleepycat.je.OperationStatus;
import com.sleepycat.je.Transaction;
import java.io.IOException;
import java.time.Instant;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

/**
 * Stores recovery information per transaction. This allows the Transaction API to return
 * transaction info with full state updates.
 */
public final class BerkeleyRecoverableProcessedTxnStore implements BerkeleyAdditionalStore {
  private static final String RECOVERABLE_TRANSACTIONS_DB_NAME = "radix.recoverable_txns";
  private static final String ACCUMULATOR_HASH_DB_NAME = "radix.accumulator_hash";
  private Database recoverableTransactionsDatabase; // Txns by index; Append-only
  private Database accumulatorDatabase; // Txns by index; Append-only

  private final AtomicReference<Instant> timestamp = new AtomicReference<>();
  private final Provider<RadixEngine<LedgerAndBFTProof>> radixEngineProvider;
  private final LedgerAccumulator ledgerAccumulator;
  private final Serialization serialization;
  private AccumulatorState accumulatorState;

  @Inject
  public BerkeleyRecoverableProcessedTxnStore(
      Serialization serialization,
      Provider<RadixEngine<LedgerAndBFTProof>> radixEngineProvider,
      LedgerAccumulator ledgerAccumulator) {
    this.serialization = serialization;
    // TODO: Fix this when we move AdditionalStore to be a RadixEngine construct rather than
    // Berkeley construct
    this.radixEngineProvider = radixEngineProvider;
    this.ledgerAccumulator = ledgerAccumulator;
  }

  @Override
  public void open(DatabaseEnvironment dbEnv) {
    recoverableTransactionsDatabase =
        dbEnv
            .getEnvironment()
            .openDatabase(
                null,
                RECOVERABLE_TRANSACTIONS_DB_NAME,
                new DatabaseConfig()
                    .setAllowCreate(true)
                    .setTransactional(true)
                    .setKeyPrefixing(true)
                    .setBtreeComparator(lexicographicalComparator()));

    accumulatorDatabase =
        dbEnv
            .getEnvironment()
            .openDatabase(
                null,
                ACCUMULATOR_HASH_DB_NAME,
                new DatabaseConfig()
                    .setAllowCreate(true)
                    .setTransactional(true)
                    .setKeyPrefixing(true)
                    .setBtreeComparator(lexicographicalComparator()));

    try (var cursor = accumulatorDatabase.openCursor(null, null)) {
      var key = new DatabaseEntry(Longs.toByteArray(Long.MAX_VALUE));
      var value = new DatabaseEntry();
      cursor.getSearchKeyRange(key, value, null);
      var status = cursor.getPrev(key, value, null);
      if (status == SUCCESS) {
        var accumulatorHash = HashCode.fromBytes(value.getData());
        var stateVersion = Longs.fromByteArray(key.getData());
        this.accumulatorState = new AccumulatorState(stateVersion, accumulatorHash);
      } else {
        this.accumulatorState = new AccumulatorState(0, HashUtils.zero256());
      }
    }
  }

  @Override
  public void close() {
    if (recoverableTransactionsDatabase != null) {
      recoverableTransactionsDatabase.close();
    }

    if (accumulatorDatabase != null) {
      accumulatorDatabase.close();
    }
  }

  public Optional<HashCode> getAccumulator(long stateVersion) {
    if (stateVersion == 0) {
      return Optional.of(HashUtils.zero256());
    }
    var key = new DatabaseEntry(Longs.toByteArray(stateVersion));
    var value = new DatabaseEntry();
    var result = accumulatorDatabase.get(null, key, value, null);
    if (result != SUCCESS) {
      return Optional.empty();
    }
    return Optional.of(HashCode.fromBytes(value.getData()));
  }

  public List<RecoverableProcessedTxn> get(long index, long limit) {
    try (var cursor = recoverableTransactionsDatabase.openCursor(null, null)) {
      var iterator =
          new Iterator<RecoverableProcessedTxn>() {
            final DatabaseEntry key = new DatabaseEntry(Longs.toByteArray(index));
            final DatabaseEntry value = new DatabaseEntry();
            OperationStatus status =
                cursor.get(key, value, Get.SEARCH, null) != null
                    ? SUCCESS
                    : OperationStatus.NOTFOUND;

            @Override
            public boolean hasNext() {
              return status == SUCCESS;
            }

            @Override
            public RecoverableProcessedTxn next() {
              if (status != SUCCESS) {
                throw new NoSuchElementException();
              }
              RecoverableProcessedTxn next;
              try {
                next =
                    serialization.fromDson(
                        Compress.uncompress(value.getData()), RecoverableProcessedTxn.class);
              } catch (IOException e) {
                throw new IllegalStateException("Failed to deserialize committed transaction.", e);
              }

              status = cursor.getNext(key, value, null);
              return next;
            }
          };
      return Streams.stream(iterator).limit(limit).toList();
    }
  }

  @Override
  public void process(
      Transaction dbTxn,
      REProcessedTxn txn,
      long stateVersion,
      Function<SystemMapKey, Optional<RawSubstateBytes>> mapper) {
    if (accumulatorState.getStateVersion() != stateVersion - 1) {
      throw new IllegalStateException("Accumulator out of sync.");
    }

    txn.stateUpdates()
        .filter(u -> u.getParsed() instanceof RoundData)
        .map(u -> (RoundData) u.getParsed())
        .filter(r -> r.timestamp() > 0)
        .map(RoundData::asInstant)
        .forEach(timestamp::set);

    var substateSerialization = radixEngineProvider.get().getSubstateSerialization();
    var stored = RecoverableProcessedTxn.from(txn, substateSerialization);
    byte[] data;
    try {
      data = Compress.compress(serialization.toDson(stored, DsonOutput.Output.ALL));
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }

    var nextAccumulatorState =
        ledgerAccumulator.accumulate(accumulatorState, txn.getTxnId().asHashCode());
    this.accumulatorState = nextAccumulatorState;

    var key = new DatabaseEntry(Longs.toByteArray(stateVersion - 1));
    var value = new DatabaseEntry(data);
    var result = recoverableTransactionsDatabase.putNoOverwrite(dbTxn, key, value);
    if (result != SUCCESS) {
      throw new IllegalStateException("Unexpected operation status " + result);
    }

    var versionEntry = new DatabaseEntry(Longs.toByteArray(nextAccumulatorState.getStateVersion()));
    var accumulatorHashEntry =
        new DatabaseEntry(nextAccumulatorState.getAccumulatorHash().asBytes());
    result = accumulatorDatabase.put(dbTxn, versionEntry, accumulatorHashEntry);
    if (result != SUCCESS) {
      throw new IllegalStateException("Unexpected operation status " + result);
    }
  }
}
