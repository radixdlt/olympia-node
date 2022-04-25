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

import com.google.inject.Inject;
import com.radixdlt.consensus.Vote;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.consensus.safety.PersistentSafetyStateStore;
import com.radixdlt.consensus.safety.SafetyState;
import com.radixdlt.monitoring.SystemCounters;
import com.radixdlt.monitoring.SystemCounters.CounterType;
import com.radixdlt.serialization.DeserializeException;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.Serialization;
import com.radixdlt.store.DatabaseEnvironment;
import com.radixdlt.utils.Longs;
import com.sleepycat.je.Cursor;
import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.Environment;
import com.sleepycat.je.LockMode;
import com.sleepycat.je.OperationStatus;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Store which persists state required to preserve the networks safety in case of a node restart.
 */
public final class BerkeleySafetyStateStore implements PersistentSafetyStateStore {
  private static final String SAFETY_STORE_NAME = "safety_store";
  private static final Logger logger = LogManager.getLogger();
  private static final long UPPER_THRESHOLD = 1000;
  private static final long LOWER_THRESHOLD = 10;

  private final DatabaseEnvironment dbEnv;
  private final Database safetyStore;
  private final SystemCounters systemCounters;
  private final AtomicLong cleanupCounter = new AtomicLong();
  private final Serialization serialization;

  @Inject
  public BerkeleySafetyStateStore(
      DatabaseEnvironment dbEnv, Serialization serialization, SystemCounters systemCounters) {
    this.dbEnv = Objects.requireNonNull(dbEnv, "dbEnv is required");
    this.serialization = Objects.requireNonNull(serialization);

    this.safetyStore = this.open();
    this.systemCounters = Objects.requireNonNull(systemCounters);

    if (Boolean.valueOf(System.getProperty("db.check_integrity", "true"))) {
      // TODO implement integrity check
    }
  }

  private void fail(String message) {
    logger.error(message);
    throw new BerkeleyStoreException(message);
  }

  private void fail(String message, Exception cause) {
    logger.error(message, cause);
    throw new BerkeleyStoreException(message, cause);
  }

  private Database open() {
    DatabaseConfig primaryConfig = new DatabaseConfig();
    primaryConfig.setAllowCreate(true);
    primaryConfig.setTransactional(true);

    try {
      // This SuppressWarnings here is valid, as ownership of the underlying
      // resource is not changed here, the resource is just accessed.
      @SuppressWarnings("resource")
      Environment env = this.dbEnv.getEnvironment();
      return env.openDatabase(null, SAFETY_STORE_NAME, primaryConfig);
    } catch (Exception e) {
      throw new BerkeleyStoreException("Error while opening database", e);
    }
  }

  @Override
  public void close() {
    if (this.safetyStore != null) {
      this.safetyStore.close();
    }
  }

  @Override
  public Optional<SafetyState> get() {
    final var start = System.nanoTime();
    try (com.sleepycat.je.Cursor cursor = this.safetyStore.openCursor(null, null)) {
      DatabaseEntry pKey = new DatabaseEntry();
      DatabaseEntry value = new DatabaseEntry();
      OperationStatus status = cursor.getLast(pKey, value, LockMode.DEFAULT);
      if (status == OperationStatus.SUCCESS) {
        addBytesRead(pKey.getSize() + value.getSize());
        try {
          final SafetyState deserializedState =
              serialization.fromDson(value.getData(), SafetyState.class);
          return Optional.of(deserializedState);
        } catch (DeserializeException ex) {
          logger.error("Failed to deserialize persisted SafetyState", ex);
          return Optional.empty();
        }
      } else {
        return Optional.empty();
      }
    } finally {
      addTime(start);
    }
  }

  @Override
  public void commitState(SafetyState safetyState) {
    this.systemCounters.increment(CounterType.PERSISTENCE_SAFETY_STORE_SAVES);

    final var start = System.nanoTime();

    final var transaction = dbEnv.getEnvironment().beginTransaction(null, null);
    try {
      final byte[] serializedState = serialization.toDson(safetyState, DsonOutput.Output.PERSIST);

      final DatabaseEntry key = new DatabaseEntry(keyFor(safetyState));
      final DatabaseEntry data = new DatabaseEntry(serializedState);

      final OperationStatus status = this.safetyStore.put(transaction, key, data);
      if (status != OperationStatus.SUCCESS) {
        fail("Database returned status " + status + " for put operation");
      } else {
        addBytesWrite(key.getSize() + data.getSize());
      }

      transaction.commit();

      cleanupUnused();
    } catch (Exception e) {
      transaction.abort();
      fail("Error while storing safety state for " + safetyState, e);
    } finally {
      addTime(start);
    }
  }

  private void cleanupUnused() {
    if (cleanupCounter.incrementAndGet() % UPPER_THRESHOLD != 0) {
      return;
    }

    final var transaction = dbEnv.getEnvironment().beginTransaction(null, null);
    try {
      long count = safetyStore.count();

      if (count < UPPER_THRESHOLD) {
        transaction.abort();
        return;
      }

      try (Cursor cursor = this.safetyStore.openCursor(transaction, null)) {
        DatabaseEntry key = new DatabaseEntry();
        DatabaseEntry value = new DatabaseEntry();

        if (cursor.getFirst(key, value, LockMode.DEFAULT) == OperationStatus.SUCCESS) {
          if (cursor.delete() != OperationStatus.SUCCESS) {
            transaction.abort();
            return;
          }
          addBytesRead(key.getSize() + value.getSize());
          count--;
        }

        while (cursor.getNext(key, value, LockMode.DEFAULT) == OperationStatus.SUCCESS
            && count > LOWER_THRESHOLD) {
          if (cursor.delete() != OperationStatus.SUCCESS) {
            transaction.abort();
            return;
          }
          addBytesRead(key.getSize() + value.getSize());
          count--;
        }
      }

      transaction.commit();
    } catch (Exception e) {
      transaction.abort();
      fail("Error while clearing unused states", e);
    }
  }

  private void addTime(long start) {
    final var elapsed = (System.nanoTime() - start + 500L) / 1000L;
    this.systemCounters.add(CounterType.ELAPSED_BDB_SAFETY_STATE, elapsed);
    this.systemCounters.increment(CounterType.COUNT_BDB_SAFETY_STATE_TOTAL);
  }

  private void addBytesRead(int bytesRead) {
    this.systemCounters.add(CounterType.COUNT_BDB_SAFETY_STATE_BYTES_READ, bytesRead);
  }

  private void addBytesWrite(int bytesWrite) {
    this.systemCounters.add(CounterType.COUNT_BDB_SAFETY_STATE_BYTES_WRITE, bytesWrite);
  }

  private byte[] keyFor(SafetyState safetyState) {
    long epoch = safetyState.getLastVote().map(Vote::getEpoch).orElse(0L);
    long view = safetyState.getLastVote().map(Vote::getView).orElse(View.genesis()).number();

    byte[] keyBytes = new byte[Long.BYTES * 2];
    Longs.copyTo(epoch, keyBytes, 0);
    Longs.copyTo(view, keyBytes, Long.BYTES);

    return keyBytes;
  }
}
