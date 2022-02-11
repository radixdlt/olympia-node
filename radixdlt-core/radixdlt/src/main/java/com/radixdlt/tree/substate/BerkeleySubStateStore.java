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

package com.radixdlt.tree.substate;

import static com.google.common.primitives.UnsignedBytes.lexicographicalComparator;

import com.google.common.base.Stopwatch;
import com.radixdlt.application.system.state.EpochData;
import com.radixdlt.atom.SubstateId;
import com.radixdlt.constraintmachine.REProcessedTxn;
import com.radixdlt.constraintmachine.REStateUpdate;
import com.radixdlt.constraintmachine.RawSubstateBytes;
import com.radixdlt.constraintmachine.SystemMapKey;
import com.radixdlt.store.DatabaseEnvironment;
import com.radixdlt.store.berkeley.BerkeleyAdditionalStore;
import com.radixdlt.tree.PMT;
import com.radixdlt.tree.storage.CachedPMTStorage;
import com.radixdlt.tree.storage.PMTCache;
import com.radixdlt.tree.storage.PMTStorage;
import com.radixdlt.tree.storage.RefCounterPMTStorage;
import com.radixdlt.utils.Bytes;
import com.radixdlt.utils.Longs;
import com.radixdlt.utils.Pair;
import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.Transaction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class BerkeleySubStateStore implements BerkeleyAdditionalStore {

  private static final Logger logger = LogManager.getLogger();
  public static final int CACHE_MAXIMUM_SIZE = 1_000_000;
  protected static final byte[] CURRENT_ROOT_KEY =
      "current_root_key".getBytes(StandardCharsets.UTF_8);

  private Database subStateTreeDatabase;
  private Database epochRootHashDatabase;

  private PMTCache pmtCache;

  private byte[] rootHash;

  private Stopwatch watch = Stopwatch.createUnstarted();

  @Override
  public void open(DatabaseEnvironment dbEnv) {
    this.subStateTreeDatabase =
        dbEnv
            .getEnvironment()
            .openDatabase(
                null,
                "radix.substate_tree",
                new DatabaseConfig()
                    .setAllowCreate(true)
                    .setTransactional(true)
                    .setKeyPrefixing(true)
                    .setBtreeComparator(lexicographicalComparator()));
    this.epochRootHashDatabase =
        dbEnv
            .getEnvironment()
            .openDatabase(
                null,
                "radix.epoch_root_hash",
                new DatabaseConfig()
                    .setAllowCreate(true)
                    .setTransactional(true)
                    .setKeyPrefixing(true)
                    .setBtreeComparator(lexicographicalComparator()));
    this.pmtCache = new PMTCache(CACHE_MAXIMUM_SIZE);
    this.rootHash = new BerkeleyStorage(this.subStateTreeDatabase, null).read(CURRENT_ROOT_KEY);
  }

  @Override
  public void close() {
    this.subStateTreeDatabase.close();
    this.epochRootHashDatabase.close();
  }

  @Override
  public void process(
      Transaction dbTxn,
      REProcessedTxn txn,
      long stateVersion,
      Function<SystemMapKey, Optional<RawSubstateBytes>> mapper) {
    watch.start();
    boolean isEpochChange = false;
    Long epoch = null;
    BerkeleyStorage berkeleyStorage = new BerkeleyStorage(this.subStateTreeDatabase, dbTxn);
    CachedPMTStorage cachedPMTStorage = new CachedPMTStorage(berkeleyStorage, pmtCache);
    PMTStorage refCounterPMTStorage = new RefCounterPMTStorage(cachedPMTStorage);
    var subStateTree = new SubStateTree(new PMT(refCounterPMTStorage, this.rootHash));
    List<Pair<SubstateId, byte[]>> values = new ArrayList<>();
    for (REStateUpdate stateUpdate : txn.stateUpdates().toList()) {
      values.add(Pair.of(stateUpdate.getId(), SubStateTree.getValue(stateUpdate.isBootUp())));
      if (stateUpdate.getParsed() instanceof EpochData epochData) {
        if (stateUpdate.isBootUp()) {
          epoch = epochData.getEpoch();
        }
        isEpochChange = true;
      }
    }
    subStateTree = subStateTree.putAll(values);
    this.rootHash = subStateTree.getRootHash();
    // This shouldn't be cached as it will get be stale eventually. We also can't use ref counting.
    berkeleyStorage.save(CURRENT_ROOT_KEY, this.rootHash);

    if (isEpochChange) {
      epochRootHashDatabase.put(
          dbTxn, new DatabaseEntry(Longs.toByteArray(epoch)), new DatabaseEntry(this.rootHash));
      if (logger.isInfoEnabled()) {
        logger.info(
            "SubState Tree Root hash: {} for epoch {}. Time spent since last epoch: {} s.",
            Bytes.toHexString(this.rootHash),
            epoch,
            watch.elapsed().toSeconds());
        logger.info(this.pmtCache.getStats());
      }
      watch.reset();
    }
    if (watch.isRunning()) {
      watch.stop();
    }
  }

  public byte[] getRootHash() {
    return rootHash;
  }

  public Database getSubStateTreeDatabase() {
    return subStateTreeDatabase;
  }

  public Database getEpochRootHashDatabase() {
    return epochRootHashDatabase;
  }
}
