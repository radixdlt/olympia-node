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

package com.radixdlt.statecomputer.substatehash;

import static com.google.common.primitives.UnsignedBytes.lexicographicalComparator;

import com.google.common.base.Stopwatch;
import com.google.common.base.Strings;
import com.google.common.io.CharSink;
import com.google.common.io.FileWriteMode;
import com.google.common.io.Files;
import com.google.inject.Inject;
import com.radixdlt.application.system.state.EpochData;
import com.radixdlt.constraintmachine.*;
import com.radixdlt.crypto.HashUtils;
import com.radixdlt.properties.RuntimeProperties;
import com.radixdlt.statecomputer.forks.Forks;
import com.radixdlt.store.DatabaseEnvironment;
import com.radixdlt.store.berkeley.BerkeleyAdditionalStore;
import com.radixdlt.utils.Bytes;
import com.radixdlt.utils.Longs;
import com.sleepycat.je.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.function.Function;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bouncycastle.util.Arrays;
import org.bouncycastle.util.encoders.Hex;

public class BerkeleySubstateAccumulatorHashStore implements BerkeleyAdditionalStore {

  private static final Logger logger = LogManager.getLogger();
  private static final byte[] SUBSTATE_ACCUMULATOR_HASH_KEY =
      "substate_accumulator_hash_key".getBytes(StandardCharsets.UTF_8);
  private static final String EPOCH_HASH_FILE_PATH =
      "radixdlt-core/radixdlt/src/main/resources/epoch-hash";
  public static final String EPOCH_HASH_FILE_SEPARATOR = "=";

  private Database substateAccumulatorHashDatabase;
  private Database epochHashDatabase;

  private byte[] currentSubstateAccumulatorHash;
  private Optional<Long> lastEpochInDbOpt;
  private Optional<Long> lastEpochInFileOpt;

  private final Stopwatch watch = Stopwatch.createUnstarted();

  private Forks forks;
  private final RuntimeProperties properties;
  private Writer writer;

  private boolean isUpdateEpochHashAccumulatorFileEnabled;

  @Inject
  public BerkeleySubstateAccumulatorHashStore(Forks forks, RuntimeProperties properties) {
    this.forks = forks;
    this.properties = properties;
  }

  @Override
  public void open(DatabaseEnvironment dbEnv) {
    this.substateAccumulatorHashDatabase = openSubstateAccumulatorHashDatabase(dbEnv);
    this.epochHashDatabase = openEpochHashDatabase(dbEnv);
    this.currentSubstateAccumulatorHash =
        getPreviousSubStateAccumulatorHash(null).orElse(new byte[0]);
    this.lastEpochInDbOpt = getLatestStoredEpoch();
    this.isUpdateEpochHashAccumulatorFileEnabled =
        properties.get("update_epoch_hash_accumulator_file.enable", false);
    if (this.isUpdateEpochHashAccumulatorFileEnabled && writer == null) {
      openEpochsHashFile();
    }
  }

  private Database openEpochHashDatabase(DatabaseEnvironment dbEnv) {
    return dbEnv
        .getEnvironment()
        .openDatabase(
            null,
            "radix.epoch_hash",
            new DatabaseConfig()
                .setAllowCreate(true)
                .setTransactional(true)
                .setKeyPrefixing(true)
                .setBtreeComparator(lexicographicalComparator()));
  }

  private Database openSubstateAccumulatorHashDatabase(DatabaseEnvironment dbEnv) {
    return dbEnv
        .getEnvironment()
        .openDatabase(
            null,
            "radix.substate_hash_accumulator",
            new DatabaseConfig()
                .setAllowCreate(true)
                .setTransactional(true)
                .setKeyPrefixing(true)
                .setBtreeComparator(lexicographicalComparator()));
  }

  @Override
  public void close() {
    this.substateAccumulatorHashDatabase.close();
    this.epochHashDatabase.close();
    if (writer != null) {
      try {
        this.writer.close();
      } catch (IOException e) {
        throw new IllegalStateException("Error when closing the epochs hash file.", e);
      }
    }
  }

  @Override
  public void process(
      Transaction dbTxn,
      REProcessedTxn txn,
      long stateVersion,
      Function<SystemMapKey, Optional<RawSubstateBytes>> mapper) {

    watch.start();
    var isEpochChange = false;
    var subStateBytes = new byte[0];
    for (REStateUpdate reStateUpdate : txn.stateUpdates().toList()) {
      subStateBytes = Arrays.concatenate(subStateBytes, getBytes(reStateUpdate));
      if (reStateUpdate.getParsed() instanceof EpochData epochData) {
        lastEpochInDbOpt = Optional.of(epochData.epoch());
        isEpochChange = true;
      }
    }

    this.currentSubstateAccumulatorHash =
        HashUtils.sha256(Arrays.concatenate(this.currentSubstateAccumulatorHash, subStateBytes))
            .asBytes();
    persistCurrentSubstateAccumulatorHash(dbTxn);

    if (isEpochChange) {
      persistEpochHash(dbTxn);
      if (logger.isInfoEnabled()) {
        logEpochHash();
      }
      if (this.isUpdateEpochHashAccumulatorFileEnabled) {
        handleEpochHashAccumulatorFileUpdate();
      }
      watch.reset();
    }
    if (watch.isRunning()) {
      watch.stop();
    }
  }

  private void persistCurrentSubstateAccumulatorHash(Transaction dbTxn) {
    var key = new DatabaseEntry(SUBSTATE_ACCUMULATOR_HASH_KEY);
    this.substateAccumulatorHashDatabase.put(
        dbTxn, key, new DatabaseEntry(this.currentSubstateAccumulatorHash));
  }

  private void logEpochHash() {
    logger.info(
        "Epoch Hash: {} for epoch {}. Time spent since last epoch: {} s.",
        Bytes.toHexString(this.currentSubstateAccumulatorHash),
        lastEpochInDbOpt,
        watch.elapsed().toSeconds());
  }

  private OperationStatus persistEpochHash(Transaction dbTxn) {
    return epochHashDatabase.put(
        dbTxn,
        new DatabaseEntry(Longs.toByteArray(lastEpochInDbOpt.orElseThrow())),
        new DatabaseEntry(this.currentSubstateAccumulatorHash));
  }

  private void handleEpochHashAccumulatorFileUpdate() {
    // we do not overwrite hashes and only append in a sequential way
    if (lastEpochInFileOpt.orElse(0L) == lastEpochInDbOpt.orElse(0L) - 1L) {
      try {
        writer.write(
            String.format(
                "%s%s%s%n",
                lastEpochInDbOpt.orElseThrow(),
                EPOCH_HASH_FILE_SEPARATOR,
                Hex.toHexString(this.currentSubstateAccumulatorHash)));
        this.lastEpochInFileOpt = lastEpochInDbOpt;
      } catch (IOException e) {
        throw new IllegalStateException("Error when writing to the epochs hash file.", e);
      }
    }
  }

  private Optional<byte[]> getPreviousSubStateAccumulatorHash(Transaction dbTxn) {
    var previousSubstateAccumulatorHash = new DatabaseEntry();
    var key = new DatabaseEntry(SUBSTATE_ACCUMULATOR_HASH_KEY);
    this.substateAccumulatorHashDatabase.get(dbTxn, key, previousSubstateAccumulatorHash, null);
    return Optional.ofNullable(previousSubstateAccumulatorHash.getData());
  }

  private Optional<Long> getLatestStoredEpoch() {
    try (Cursor cursor = this.epochHashDatabase.openCursor(null, null)) {
      var key = new DatabaseEntry();
      var data = new DatabaseEntry();
      cursor.getLast(key, data, null);
      return key.getData() == null
          ? Optional.empty()
          : Optional.of(Longs.fromByteArray(key.getData()));
    }
  }

  private byte[] getBytes(REStateUpdate reStateUpdate) {
    var op = reStateUpdate.isBootUp() ? new byte[] {0} : new byte[] {1};
    var id = reStateUpdate.getId() != null ? reStateUpdate.getId().asBytes() : new byte[0];
    var type = new byte[] {reStateUpdate.typeByte()};
    var parsed = getBytes(reStateUpdate.getParsed());
    var stateBuf = reStateUpdate.getRawSubstateBytes().getData();
    var instructionIndex = new byte[] {(byte) reStateUpdate.getInstructionIndex()};
    return Arrays.concatenate(Arrays.concatenate(op, id, type, parsed), stateBuf, instructionIndex);
  }

  private byte[] getBytes(Particle particle) {
    return getSubstateSerializer().serialize(particle);
  }

  private SubstateSerialization getSubstateSerializer() {
    return this.forks.get(lastEpochInDbOpt.orElse(0L)).serialization();
  }

  /* When we are updating the epoch hash file (appending new epoch hashes), we must not have processed epochs greater
  than the last one in the epoch hash file. Otherwise, there is no way of appending new epochs without having gaps. */
  private void assertEpochInTheDbIsNotGreaterThanEpochInTheFile(Optional<Long> lastEpochInFileOpt)
      throws IllegalStateException {
    boolean isValid = true;
    if (lastEpochInFileOpt.isEmpty()) {
      if (this.lastEpochInDbOpt.isPresent()) {
        // there is an epoch in db, but none in the file
        isValid = false;
      }
    } else if (this.lastEpochInDbOpt.map(it -> it > lastEpochInFileOpt.get()).orElse(false)) {
      // we have an epoch in the file and need to check it is not greater than the one in the db (if
      // any)
      isValid = false;
    }
    if (!isValid) {
      throw new IllegalStateException(
          String.format(
              "Epoch in db %s is greater than epoch in file %s",
              this.lastEpochInDbOpt, lastEpochInFileOpt));
    }
  }

  private Optional<Long> getLastEpochInFile(File file) throws IOException {
    try (BufferedReader input = new BufferedReader(new FileReader(file))) {
      var last = Optional.<String>empty();
      String line;
      while (!Strings.isNullOrEmpty((line = input.readLine()))) {
        last = Optional.ofNullable(line);
      }
      return last.map(it -> it.split(EPOCH_HASH_FILE_SEPARATOR))
          .map(it -> it[0])
          .map(Long::valueOf);
    }
  }

  private void openEpochsHashFile() {
    var projectFolder = System.getProperty("user.dir");
    File file = new File(projectFolder, EPOCH_HASH_FILE_PATH);
    try {
      this.lastEpochInFileOpt = getLastEpochInFile(file);
      assertEpochInTheDbIsNotGreaterThanEpochInTheFile(lastEpochInFileOpt);
      CharSink charSink = Files.asCharSink(file, StandardCharsets.UTF_8, FileWriteMode.APPEND);
      writer = charSink.openStream();
    } catch (IOException e) {
      throw new IllegalStateException("Error when opening the epochs hash file.", e);
    }
  }
}
