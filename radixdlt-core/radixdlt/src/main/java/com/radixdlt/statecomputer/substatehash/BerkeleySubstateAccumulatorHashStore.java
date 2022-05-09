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
import com.google.inject.Provider;
import com.google.inject.name.Named;
import com.radixdlt.application.system.state.EpochData;
import com.radixdlt.constraintmachine.*;
import com.radixdlt.crypto.HashUtils;
import com.radixdlt.engine.RadixEngine;
import com.radixdlt.networks.Network;
import com.radixdlt.statecomputer.LedgerAndBFTProof;
import com.radixdlt.store.DatabaseEnvironment;
import com.radixdlt.store.berkeley.BerkeleyAdditionalStore;
import com.radixdlt.utils.Bytes;
import com.radixdlt.utils.Longs;
import com.radixdlt.utils.Pair;
import com.sleepycat.je.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bouncycastle.util.Arrays;
import org.bouncycastle.util.encoders.Hex;

public class BerkeleySubstateAccumulatorHashStore implements BerkeleyAdditionalStore {

  private static final Logger logger = LogManager.getLogger();

  public static final String UPDATE_EPOCH_HASH_FILE_ENABLE_PROPERTY_NAME =
      "update_epoch_hash_file.enable";
  public static final String VERIFY_EPOCH_HASH_ENABLE_PROPERTY_NAME = "verify_epoch_hash.enable";

  public static final String CURRENT_NETWORK = "current_network";

  public static final String EPOCH_HASH_FILENAME = "epoch-hash";

  private static final String EPOCH_HASH_FILE_WRITE_DIR =
      String.join(
          File.separator,
          System.getProperty("user.dir"),
          "radixdlt-core",
          "radixdlt",
          "src",
          "main",
          "resources");

  private static final String EPOCH_HASH_FILE_SEPARATOR = "=";

  private static final byte[] LAST_EPOCH_VERIFIED_KEY =
      "last_epoch_verified".getBytes(StandardCharsets.UTF_8);

  public static final String ERROR_WHEN_OPENING_THE_EPOCHS_HASH_FILE =
      "Error when opening the epochs hash file.";

  private Database substateAccumulatorHashDatabase;
  private Database epochHashDatabase;
  private Database lastEpochHashVerifiedDatabase;

  private byte[] currentSubstateAccumulatorHash;
  private Optional<Long> lastEpochInDbOpt;
  private Optional<Long> lastEpochInFileOpt;
  private Optional<Long> lastStateVersionInDbOpt;
  private Optional<Long> lastEpochHashVerified;

  private final Stopwatch timeSpentOnSubstateAccumulatorThisEpoch = Stopwatch.createUnstarted();

  private final Provider<RadixEngine<LedgerAndBFTProof>> radixEngineProvider;

  private Writer epochsHashFileWriter;
  private BufferedReader epochsHashFileBufferedReader;

  private final Network currentNetwork;

  private final boolean isUpdateEpochHashFileEnabled;
  private final boolean isVerifyEpochHashEnabled;

  @Inject
  public BerkeleySubstateAccumulatorHashStore(
      Provider<RadixEngine<LedgerAndBFTProof>> radixEngineProvider,
      @Named(CURRENT_NETWORK) Network currentNetwork,
      @Named(UPDATE_EPOCH_HASH_FILE_ENABLE_PROPERTY_NAME) boolean isUpdateEpochHashFileEnabled,
      @Named(VERIFY_EPOCH_HASH_ENABLE_PROPERTY_NAME) boolean isVerifyEpochHashEnabled) {
    this.radixEngineProvider = radixEngineProvider;
    this.currentNetwork = currentNetwork;
    this.isUpdateEpochHashFileEnabled = isUpdateEpochHashFileEnabled;
    this.isVerifyEpochHashEnabled = isVerifyEpochHashEnabled;
    if (this.isUpdateEpochHashFileEnabled && this.isVerifyEpochHashEnabled) {
      throw new IllegalStateException(
          String.format(
              "Either %s or %s should be enabled.",
              UPDATE_EPOCH_HASH_FILE_ENABLE_PROPERTY_NAME, VERIFY_EPOCH_HASH_ENABLE_PROPERTY_NAME));
    }
  }

  @Override
  public void open(DatabaseEnvironment dbEnv) {
    this.substateAccumulatorHashDatabase = openDatabase(dbEnv, "radix.substate_hash_accumulator");
    this.epochHashDatabase = openDatabase(dbEnv, "radix.epoch_hash");
    this.lastEpochHashVerifiedDatabase = openDatabase(dbEnv, "radix.last_epoch_hash_verified");
    this.currentSubstateAccumulatorHash =
        getPreviousSubStateAccumulatorHash().orElse(HashUtils.zero256().asBytes());
    this.lastEpochInDbOpt = getLatestStoredEpoch();
    this.lastStateVersionInDbOpt = getPreviousSubStateAccumulatorStateVersion();
    try (BufferedReader bufferedReader = openEpochsHashFileAsRead()) {
      this.lastEpochInFileOpt = getLastEpochInFile(bufferedReader);
    } catch (IOException e) {
      throw new IllegalStateException(ERROR_WHEN_OPENING_THE_EPOCHS_HASH_FILE, e);
    }
    if (this.isUpdateEpochHashFileEnabled) {
      assertEpochInTheDbIsNotGreaterThanEpochInTheFile(this.lastEpochInFileOpt);
      this.epochsHashFileWriter = openEpochsHashFileAsAppend();
    } else if (isVerifyEpochHashEnabled) {
      try {
        this.epochsHashFileBufferedReader = openEpochsHashFileAsRead();
        this.lastEpochHashVerified = getLastEpochHashVerified();
        moveFileReaderToLastEpochVerified(
            this.epochsHashFileBufferedReader, this.lastEpochHashVerified);
      } catch (IOException e) {
        throw new IllegalStateException(ERROR_WHEN_OPENING_THE_EPOCHS_HASH_FILE, e);
      }
    }
  }

  private Database openDatabase(DatabaseEnvironment dbEnv, String databaseName) {
    return dbEnv
        .getEnvironment()
        .openDatabase(
            null,
            databaseName,
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
    this.lastEpochHashVerifiedDatabase.close();
    closeEpochHashFile(this.epochsHashFileWriter);
    closeEpochHashFile(this.epochsHashFileBufferedReader);
  }

  private void closeEpochHashFile(Closeable epochHashFile) {
    if (epochHashFile != null) {
      try {
        epochHashFile.close();
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

    assertLastStateVersionIsAsExpected(stateVersion);

    this.timeSpentOnSubstateAccumulatorThisEpoch.start();
    var isEpochChange = false;
    Long currentEpoch = null;
    Long nextEpoch;
    var substateBytes = new byte[0];
    for (REStateUpdate reStateUpdate : txn.stateUpdates().toList()) {
      substateBytes = Arrays.concatenate(substateBytes, getBytes(reStateUpdate));
      if (reStateUpdate.getParsed() instanceof EpochData epochData) {
        nextEpoch = epochData.epoch();
        currentEpoch = nextEpoch - 1;
        isEpochChange = true;
      }
    }

    this.currentSubstateAccumulatorHash =
        HashUtils.sha256(Arrays.concatenate(this.currentSubstateAccumulatorHash, substateBytes))
            .asBytes();
    persistCurrentSubstateAccumulatorHash(dbTxn, this.lastStateVersionInDbOpt, stateVersion);
    this.lastStateVersionInDbOpt = Optional.of(stateVersion);

    if (isEpochChange) {
      persistEpochHash(dbTxn, currentEpoch);
      this.lastEpochInDbOpt = Optional.of(currentEpoch);
      logEpochHash();
      if (this.isUpdateEpochHashFileEnabled) {
        handleEpochHashAccumulatorFileUpdate();
      } else if (this.isVerifyEpochHashEnabled) {
        if (isLastEpochInFileGreaterThanLastEpochHashVerified()) {
          handleEpochHashCheck(currentEpoch);
          persistLastEpochHashVerified(dbTxn, currentEpoch);
          this.lastEpochInDbOpt = Optional.of(currentEpoch);
          this.lastEpochHashVerified = Optional.of(currentEpoch);
        } else {
          logger.debug(
              "Epoch {} will not be verified as it could not be found in epoch file.",
              currentEpoch);
        }
      }
      this.timeSpentOnSubstateAccumulatorThisEpoch.reset();
    }
    if (this.timeSpentOnSubstateAccumulatorThisEpoch.isRunning()) {
      this.timeSpentOnSubstateAccumulatorThisEpoch.stop();
    }
  }

  private boolean isLastEpochInFileGreaterThanLastEpochHashVerified() {
    return this.lastEpochInFileOpt.orElseGet(
            () -> {
              logger.warn(
                  "Epoch hash verification has been enabled but epoch hash file seems to be"
                      + " empty.");
              return 0L;
            })
        > this.lastEpochHashVerified.orElse(0L);
  }

  private void assertLastStateVersionIsAsExpected(long stateVersion) {
    long expectedStateVersion = stateVersion - 1;
    if (this.lastStateVersionInDbOpt.orElse(0L) != expectedStateVersion) {
      throw new IllegalStateException(
          String.format(
              "The substate hash accumulator state version has got out of sync with the ledger (It"
                  + " is expected to be at %s, but is at %s) - please clean the ledger and start"
                  + " again.",
              expectedStateVersion, this.lastStateVersionInDbOpt));
    }
  }

  private void persistCurrentSubstateAccumulatorHash(
      Transaction dbTxn, Optional<Long> currentStateVersion, long nextStateVersion) {
    currentStateVersion.ifPresent(
        it ->
            this.substateAccumulatorHashDatabase.delete(
                dbTxn, new DatabaseEntry(Longs.toByteArray(it))));
    var key = new DatabaseEntry(Longs.toByteArray(nextStateVersion));
    this.substateAccumulatorHashDatabase.put(
        dbTxn, key, new DatabaseEntry(this.currentSubstateAccumulatorHash));
  }

  private void logEpochHash() {
    if (logger.isInfoEnabled()) {
      logger.info(
          "Epoch Hash: {} for epoch {}. Time spent since last epoch: {} ms.",
          Bytes.toHexString(this.currentSubstateAccumulatorHash),
          this.lastEpochInDbOpt,
          this.timeSpentOnSubstateAccumulatorThisEpoch.elapsed().toMillis());
    }
  }

  private void persistEpochHash(Transaction dbTxn, long currentEpoch) {
    this.epochHashDatabase.put(
        dbTxn,
        new DatabaseEntry(Longs.toByteArray(currentEpoch)),
        new DatabaseEntry(this.currentSubstateAccumulatorHash));
  }

  private void persistLastEpochHashVerified(Transaction dbTxn, long currentEpoch) {
    this.lastEpochHashVerifiedDatabase.put(
        dbTxn,
        new DatabaseEntry(LAST_EPOCH_VERIFIED_KEY),
        new DatabaseEntry(Longs.toByteArray(currentEpoch)));
  }

  private void handleEpochHashAccumulatorFileUpdate() {
    // We do not overwrite hashes and only append in a sequential way
    var lastEpochInDB =
        this.lastEpochInDbOpt.orElseThrow(
            () -> new IllegalStateException("Last epoch in db is not expected to be empty."));
    if (this.lastEpochInFileOpt.orElse(0L) == lastEpochInDB - 1L) {
      logger.debug("Writing epoch hash for epoch {} to the epoch file.", lastEpochInDB);
      try {
        this.epochsHashFileWriter.write(
            String.format(
                "%s%s%s%n",
                lastEpochInDB,
                EPOCH_HASH_FILE_SEPARATOR,
                Hex.toHexString(this.currentSubstateAccumulatorHash)));
        this.epochsHashFileWriter.flush();
        this.lastEpochInFileOpt = lastEpochInDbOpt;
      } catch (IOException e) {
        throw new IllegalStateException("Error when writing to the epochs hash file.", e);
      }
    } else if (this.lastEpochInFileOpt.orElse(0L) > lastEpochInDB - 1L) {
      logger.debug(
          "Epoch hash for epoch {} is already written in epoch file. Skipping.", lastEpochInDB);
    }
  }

  private void handleEpochHashCheck(Long currentEpoch) {
    var currentEpochAndHashInFile =
        getCurrentEpochAndHashInFile(this.epochsHashFileBufferedReader)
            .orElseThrow(
                () ->
                    new IllegalStateException("No epoch and hash could be read from epoch file."));
    if (!Objects.equals(currentEpochAndHashInFile.getFirst(), currentEpoch)) {
      throw new IllegalStateException(
          String.format(
              "Current epoch in file %s is out of sync with the current epoch being processed %s -"
                  + " please clean the ledger and start again.",
              currentEpochAndHashInFile.getFirst(), currentEpoch));
    }
    if (!java.util.Arrays.equals(
        Hex.decode(currentEpochAndHashInFile.getSecond()), currentSubstateAccumulatorHash)) {
      throw new IllegalStateException(
          String.format(
              "The computed hash %s for epoch %s does not match the hash in the file %s.",
              Hex.toHexString(this.currentSubstateAccumulatorHash),
              currentEpoch,
              currentEpochAndHashInFile.getSecond()));
    }
  }

  private Optional<byte[]> getPreviousSubStateAccumulatorHash() {
    try (Cursor cursor = this.substateAccumulatorHashDatabase.openCursor(null, null)) {
      var key = new DatabaseEntry();
      var data = new DatabaseEntry();
      cursor.getLast(key, data, null);
      return data.getData() == null ? Optional.empty() : Optional.of(data.getData());
    }
  }

  private Optional<Long> getPreviousSubStateAccumulatorStateVersion() {
    try (Cursor cursor = this.substateAccumulatorHashDatabase.openCursor(null, null)) {
      var key = new DatabaseEntry();
      var data = new DatabaseEntry();
      cursor.getLast(key, data, null);
      return key.getData() == null
          ? Optional.empty()
          : Optional.of(Longs.fromByteArray(key.getData()));
    }
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

  private Optional<Long> getLastEpochHashVerified() {
    var data = new DatabaseEntry();
    this.lastEpochHashVerifiedDatabase.get(
        null, new DatabaseEntry(LAST_EPOCH_VERIFIED_KEY), data, null);
    return data.getData() == null
        ? Optional.empty()
        : Optional.of(Longs.fromByteArray(data.getData()));
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
    return this.radixEngineProvider.get().getSubstateSerialization();
  }

  /* When we are updating the epoch hash file (appending new epoch hashes), we must not have processed epochs greater
  than the last one in the epoch hash file. Otherwise, there is no way of appending new epochs without having gaps. */
  private void assertEpochInTheDbIsNotGreaterThanEpochInTheFile(Optional<Long> lastEpochInFileOpt)
      throws IllegalStateException {
    boolean isValid = true;
    if (lastEpochInFileOpt.isEmpty()) {
      if (this.lastEpochInDbOpt.isPresent()) {
        // There is an epoch in db, but none in the file
        isValid = false;
      }
    } else if (this.lastEpochInDbOpt.map(it -> it > this.lastEpochInFileOpt.get()).orElse(false)) {
      // We have an epoch in the file and need to check it is not greater than the one in the db (if
      // any)
      isValid = false;
    }
    if (!isValid) {
      throw new IllegalStateException(
          String.format(
              "The substate accumulator by epoch file has got out of sync with the database (the"
                  + " file is at epoch %s, but the database is at %s) - so the file can't continue"
                  + " to be written - please clean the ledger and start again.",
              lastEpochInFileOpt, this.lastEpochInDbOpt));
    }
  }

  private Optional<Long> getLastEpochInFile(BufferedReader bufferedReader) throws IOException {
    var last = Optional.<String>empty();
    String line;
    while (!Strings.isNullOrEmpty((line = bufferedReader.readLine()))) {
      last = Optional.ofNullable(line);
    }
    return last.map(it -> it.split(EPOCH_HASH_FILE_SEPARATOR)).map(it -> it[0]).map(Long::valueOf);
  }

  private void moveFileReaderToLastEpochVerified(
      BufferedReader bufferedReader, Optional<Long> lastEpochHashVerifiedOpt) throws IOException {
    if (lastEpochHashVerifiedOpt.isPresent()) {
      String line;
      while (!Strings.isNullOrEmpty((line = bufferedReader.readLine()))) {
        if (Long.parseLong(line.split(EPOCH_HASH_FILE_SEPARATOR)[0])
            == lastEpochHashVerified.get()) {
          break;
        }
      }
    }
  }

  private Optional<Pair<Long, String>> getCurrentEpochAndHashInFile(BufferedReader bufferedReader) {
    try {
      var line = Optional.ofNullable(bufferedReader.readLine());
      return line.map(it -> it.split(EPOCH_HASH_FILE_SEPARATOR))
          .map(it -> Pair.of(Long.valueOf(it[0]), it[1]));
    } catch (Exception e) {
      throw new IllegalStateException("Error when reading the epochs hash file.", e);
    }
  }

  private Writer openEpochsHashFileAsAppend() {
    try {
      File epochHashFile =
          new File(EPOCH_HASH_FILE_WRITE_DIR, getEpochHashFilenameForCurrentNetwork());
      CharSink charSink =
          Files.asCharSink(epochHashFile, StandardCharsets.UTF_8, FileWriteMode.APPEND);
      return charSink.openStream();
    } catch (IOException e) {
      throw new IllegalStateException(ERROR_WHEN_OPENING_THE_EPOCHS_HASH_FILE, e);
    }
  }

  private String getEpochHashFilenameForCurrentNetwork() {
    return EPOCH_HASH_FILENAME + "." + this.currentNetwork.name().toLowerCase() + ".txt";
  }

  private BufferedReader openEpochsHashFileAsRead() {
    var epochHashFileResourcePath = File.separator + getEpochHashFilenameForCurrentNetwork();
    return new BufferedReader(
        new InputStreamReader(
            Objects.requireNonNull(
                this.getClass().getResourceAsStream(epochHashFileResourcePath))));
  }

  public Database getEpochHashDatabase() {
    return epochHashDatabase;
  }
}
