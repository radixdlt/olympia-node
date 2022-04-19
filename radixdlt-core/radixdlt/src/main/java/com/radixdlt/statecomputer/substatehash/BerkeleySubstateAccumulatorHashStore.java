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
import com.google.inject.Inject;
import com.radixdlt.application.system.state.EpochData;
import com.radixdlt.constraintmachine.*;
import com.radixdlt.crypto.HashUtils;
import com.radixdlt.statecomputer.forks.Forks;
import com.radixdlt.store.DatabaseEnvironment;
import com.radixdlt.store.berkeley.BerkeleyAdditionalStore;
import com.radixdlt.utils.Bytes;
import com.radixdlt.utils.Longs;
import com.sleepycat.je.*;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.function.Function;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bouncycastle.util.Arrays;

public class BerkeleySubstateAccumulatorHashStore implements BerkeleyAdditionalStore {

  private static final Logger logger = LogManager.getLogger();
  protected static final byte[] SUBSTATE_ACCUMULATOR_HASH_KEY =
      "substate_accumulator_hash_key".getBytes(StandardCharsets.UTF_8);

  private Database substateAccumulatorHashDatabase;
  private Database epochHashDatabase;

  private byte[] currentSubstateAccumulatorHash;
  private long epoch;

  private final Stopwatch watch = Stopwatch.createUnstarted();

  private Forks forks;

  @Inject
  public BerkeleySubstateAccumulatorHashStore(Forks forks) {
    this.forks = forks;
  }

  @Override
  public void open(DatabaseEnvironment dbEnv) {
    this.substateAccumulatorHashDatabase =
        dbEnv
            .getEnvironment()
            .openDatabase(
                null,
                "radix.substate_hash_accumulator",
                new DatabaseConfig()
                    .setAllowCreate(true)
                    .setTransactional(true)
                    .setKeyPrefixing(true)
                    .setBtreeComparator(lexicographicalComparator()));
    this.epochHashDatabase =
        dbEnv
            .getEnvironment()
            .openDatabase(
                null,
                "radix.epoch_hash",
                new DatabaseConfig()
                    .setAllowCreate(true)
                    .setTransactional(true)
                    .setKeyPrefixing(true)
                    .setBtreeComparator(lexicographicalComparator()));
    this.currentSubstateAccumulatorHash =
        getPreviousSubStateAccumulatorHash(null).orElse(new byte[0]);
    this.epoch = getLatestStoredEpoch();
  }

  @Override
  public void close() {
    this.substateAccumulatorHashDatabase.close();
    this.epochHashDatabase.close();
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
        epoch = epochData.epoch();
        isEpochChange = true;
      }
    }

    this.currentSubstateAccumulatorHash =
        HashUtils.sha256(Arrays.concatenate(this.currentSubstateAccumulatorHash, subStateBytes))
            .asBytes();
    var key = new DatabaseEntry(SUBSTATE_ACCUMULATOR_HASH_KEY);
    this.substateAccumulatorHashDatabase.put(
        dbTxn, key, new DatabaseEntry(this.currentSubstateAccumulatorHash));

    if (isEpochChange) {
      epochHashDatabase.put(
          dbTxn,
          new DatabaseEntry(Longs.toByteArray(epoch)),
          new DatabaseEntry(this.currentSubstateAccumulatorHash));
      if (logger.isInfoEnabled()) {
        logger.info(
            "Epoch Hash: {} for epoch {}. Time spent since last epoch: {} s.",
            Bytes.toHexString(this.currentSubstateAccumulatorHash),
            epoch,
            watch.elapsed().toSeconds());
      }
      watch.reset();
    }
    if (watch.isRunning()) {
      watch.stop();
    }
  }

  private Optional<byte[]> getPreviousSubStateAccumulatorHash(Transaction dbTxn) {
    var previousSubstateAccumulatorHash = new DatabaseEntry();
    var key = new DatabaseEntry(SUBSTATE_ACCUMULATOR_HASH_KEY);
    this.substateAccumulatorHashDatabase.get(dbTxn, key, previousSubstateAccumulatorHash, null);
    return Optional.ofNullable(previousSubstateAccumulatorHash.getData());
  }

  private long getLatestStoredEpoch() {
    try (Cursor cursor = this.epochHashDatabase.openCursor(null, null)) {
      var key = new DatabaseEntry();
      var data = new DatabaseEntry();
      cursor.getLast(key, data, null);
      return key.getData() == null ? 0 : Longs.fromByteArray(key.getData());
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
    return this.forks.get(epoch).serialization();
  }
}
