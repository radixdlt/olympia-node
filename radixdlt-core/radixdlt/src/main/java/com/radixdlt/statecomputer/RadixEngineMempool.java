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

package com.radixdlt.statecomputer;

import com.google.common.collect.Sets;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.radixdlt.atom.SubstateId;
import com.radixdlt.atom.Txn;
import com.radixdlt.constraintmachine.REProcessedTxn;
import com.radixdlt.constraintmachine.REStateUpdate;
import com.radixdlt.engine.RadixEngine;
import com.radixdlt.engine.RadixEngineException;
import com.radixdlt.engine.RadixEngineResult;
import com.radixdlt.identifiers.AID;
import com.radixdlt.mempool.Mempool;
import com.radixdlt.mempool.MempoolDuplicateException;
import com.radixdlt.mempool.MempoolFullException;
import com.radixdlt.mempool.MempoolMaxSize;
import com.radixdlt.mempool.MempoolMetadata;
import com.radixdlt.mempool.MempoolRejectedException;
import com.radixdlt.utils.Pair;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** A mempool which uses internal radix engine to be more efficient. */
@Singleton
public final class RadixEngineMempool implements Mempool<REProcessedTxn> {
  private static final Logger logger = LogManager.getLogger();

  private final ConcurrentHashMap<AID, Pair<REProcessedTxn, MempoolMetadata>> data =
      new ConcurrentHashMap<>();
  private final Map<SubstateId, Set<AID>> substateIndex = new ConcurrentHashMap<>();
  private final RadixEngine<LedgerAndBFTProof> radixEngine;
  private final int maxSize;

  @Inject
  public RadixEngineMempool(
      RadixEngine<LedgerAndBFTProof> radixEngine, @MempoolMaxSize int maxSize) {
    if (maxSize <= 0) {
      throw new IllegalArgumentException("mempool.maxSize must be positive: " + maxSize);
    }
    this.maxSize = maxSize;
    this.radixEngine = radixEngine;
  }

  public <T> T getData(Function<Map<AID, Pair<REProcessedTxn, MempoolMetadata>>, T> mapper) {
    return mapper.apply(data);
  }

  @Override
  public REProcessedTxn add(Txn txn) throws MempoolRejectedException {
    if (this.data.size() >= maxSize) {
      throw new MempoolFullException(this.data.size(), maxSize);
    }

    if (this.data.containsKey(txn.getId())) {
      throw new MempoolDuplicateException(
          String.format("Mempool already has command %s", txn.getId()));
    }

    final RadixEngineResult<LedgerAndBFTProof> result;
    try {
      RadixEngine.RadixEngineBranch<LedgerAndBFTProof> checker = radixEngine.transientBranch();
      result = checker.execute(List.of(txn));
    } catch (RadixEngineException e) {
      // TODO: allow missing dependency atoms to live for a certain amount of time
      throw new MempoolRejectedException(e);
    } finally {
      radixEngine.deleteBranches();
    }

    var mempoolTxn = MempoolMetadata.create(System.currentTimeMillis());
    var data = Pair.of(result.getProcessedTxn(), mempoolTxn);
    this.data.put(txn.getId(), data);
    result
        .getProcessedTxn()
        .substateDependencies()
        .forEach(substateId -> substateIndex.merge(substateId, Set.of(txn.getId()), Sets::union));

    return result.getProcessedTxn();
  }

  @Override
  public List<Txn> committed(List<REProcessedTxn> transactions) {
    final var removed = new ArrayList<Txn>();
    final var committedIds =
        transactions.stream().map(p -> p.getTxn().getId()).collect(Collectors.toSet());

    transactions.stream()
        .flatMap(REProcessedTxn::stateUpdates)
        .filter(REStateUpdate::isShutDown)
        .forEach(
            instruction -> {
              var substateId = instruction.getId();
              Set<AID> txnIds = substateIndex.remove(substateId);
              if (txnIds == null) {
                return;
              }

              for (var txnId : txnIds) {
                var toRemove = data.remove(txnId);
                // TODO: Cleanup
                if (toRemove != null
                    && !committedIds.contains(toRemove.getFirst().getTxn().getId())) {
                  removed.add(toRemove.getFirst().getTxn());
                }
              }
            });

    if (!removed.isEmpty()) {
      logger.debug("Evicting {} txns from mempool", removed.size());
    }

    return removed;
  }

  @Override
  public List<Txn> getTxns(int count, List<REProcessedTxn> prepared) {
    // TODO: Order by highest fees paid
    var copy = new TreeSet<>(data.keySet());
    prepared.stream()
        .flatMap(REProcessedTxn::stateUpdates)
        .filter(REStateUpdate::isShutDown)
        .flatMap(i -> substateIndex.getOrDefault(i.getId(), Set.of()).stream())
        .distinct()
        .forEach(copy::remove);

    var txns = new ArrayList<Txn>();

    for (int i = 0; i < count && !copy.isEmpty(); i++) {
      var txId = copy.first();
      copy.remove(txId);
      var txnData = data.get(txId);
      txnData
          .getFirst()
          .stateUpdates()
          .filter(REStateUpdate::isShutDown)
          .flatMap(inst -> substateIndex.getOrDefault(inst.getId(), Set.of()).stream())
          .distinct()
          .forEach(copy::remove);

      txns.add(txnData.getFirst().getTxn());
    }

    return txns;
  }

  public Set<SubstateId> getShuttingDownSubstates() {
    return new HashSet<>(substateIndex.keySet());
  }

  @Override
  public List<Txn> scanUpdateAndGet(
      Predicate<MempoolMetadata> predicate, Consumer<MempoolMetadata> operator) {
    return this.data.values().stream()
        .filter(e -> predicate.test(e.getSecond()))
        .peek(e -> operator.accept(e.getSecond()))
        .map(e -> e.getFirst().getTxn())
        .toList();
  }

  public int getCount() {
    return this.data.size();
  }

  @Override
  public String toString() {
    return String.format(
        "%s[%x:%s/%s]",
        getClass().getSimpleName(), System.identityHashCode(this), this.data.size(), maxSize);
  }
}
