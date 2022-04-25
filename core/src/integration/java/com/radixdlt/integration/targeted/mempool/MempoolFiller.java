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

package com.radixdlt.integration.targeted.mempool;

import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import com.radixdlt.application.tokens.TokenUtils;
import com.radixdlt.atom.TxBuilderException;
import com.radixdlt.atom.Txn;
import com.radixdlt.atom.TxnConstructionRequest;
import com.radixdlt.consensus.HashSigner;
import com.radixdlt.consensus.bft.Self;
import com.radixdlt.monitoring.SystemCounters;
import com.radixdlt.engine.RadixEngine;
import com.radixdlt.environment.EventDispatcher;
import com.radixdlt.environment.EventProcessor;
import com.radixdlt.environment.RemoteEventDispatcher;
import com.radixdlt.environment.ScheduledEventDispatcher;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.mempool.MempoolAdd;
import com.radixdlt.network.p2p.PeersView;
import com.radixdlt.keys.LocalSigner;
import com.radixdlt.statecomputer.LedgerAndBFTProof;
import com.radixdlt.statecomputer.RadixEngineMempool;
import com.radixdlt.utils.UInt256;
import java.util.ArrayList;
import java.util.Random;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** Periodically fills the mempool with valid transactions */
public final class MempoolFiller {
  private static final Logger logger = LogManager.getLogger();
  private final RadixEngine<LedgerAndBFTProof> radixEngine;

  private final RemoteEventDispatcher<MempoolAdd> remoteMempoolAddEventDispatcher;
  private final EventDispatcher<MempoolAdd> mempoolAddEventDispatcher;

  private final RadixEngineMempool radixEngineMempool;
  private final ScheduledEventDispatcher<ScheduledMempoolFill> mempoolFillDispatcher;
  private final SystemCounters systemCounters;
  private final PeersView peersView;
  private final Random random;
  private final HashSigner hashSigner;
  private final REAddr account;

  private boolean enabled = false;
  private int numTransactions;
  private boolean sendToSelf = false;

  @Inject
  public MempoolFiller(
      @Self REAddr account,
      @LocalSigner HashSigner hashSigner,
      RadixEngineMempool radixEngineMempool,
      RadixEngine<LedgerAndBFTProof> radixEngine,
      EventDispatcher<MempoolAdd> mempoolAddEventDispatcher,
      RemoteEventDispatcher<MempoolAdd> remoteMempoolAddEventDispatcher,
      ScheduledEventDispatcher<ScheduledMempoolFill> mempoolFillDispatcher,
      PeersView peersView,
      Random random,
      SystemCounters systemCounters) {
    this.account = account;
    this.hashSigner = hashSigner;
    this.radixEngine = radixEngine;
    this.radixEngineMempool = radixEngineMempool;
    this.mempoolAddEventDispatcher = mempoolAddEventDispatcher;
    this.remoteMempoolAddEventDispatcher = remoteMempoolAddEventDispatcher;
    this.mempoolFillDispatcher = mempoolFillDispatcher;
    this.peersView = peersView;
    this.random = random;
    this.systemCounters = systemCounters;
  }

  public EventProcessor<MempoolFillerUpdate> mempoolFillerUpdateEventProcessor() {
    return update -> {
      update.numTransactions().ifPresent(numTx -> this.numTransactions = numTx);
      update.sendToSelf().ifPresent(sndToSelf -> this.sendToSelf = sndToSelf);

      if (update.enabled() == enabled) {
        update.onError("Already " + (enabled ? "enabled." : "disabled."));
        return;
      }

      logger.info("Mempool Filler: Updating {}", update.enabled());
      update.onSuccess();

      if (update.enabled()) {
        enabled = true;
        mempoolFillDispatcher.dispatch(ScheduledMempoolFill.create(), 50);
      } else {
        enabled = false;
      }
    };
  }

  public EventProcessor<ScheduledMempoolFill> scheduledMempoolFillEventProcessor() {
    return ignored -> {
      if (!enabled) {
        return;
      }

      var minSize = UInt256.TWO.multiply(UInt256.TEN.pow(TokenUtils.SUB_UNITS_POW_10 - 4));
      var shuttingDown = radixEngineMempool.getShuttingDownSubstates();
      var txnConstructionRequest =
          TxnConstructionRequest.create()
              .feePayer(account)
              .splitNative(REAddr.ofNativeToken(), account, minSize)
              .avoidSubstates(shuttingDown);

      var txns = new ArrayList<Txn>();
      for (int i = 0; i < numTransactions; i++) {
        try {
          var builder = radixEngine.construct(txnConstructionRequest);
          shuttingDown.addAll(builder.toLowLevelBuilder().remoteDownSubstate());
          var txn = builder.signAndBuild(hashSigner::sign);
          txns.add(txn);
        } catch (TxBuilderException e) {
          break;
        }
      }

      if (txns.size() == 1) {
        logger.info(
            "Mempool Filler mempool: {} Adding txn {} to mempool...",
            systemCounters.get(SystemCounters.CounterType.MEMPOOL_CURRENT_SIZE),
            txns.get(0).getId());
      } else {
        logger.info(
            "Mempool Filler mempool: {} Adding {} txns to mempool...",
            systemCounters.get(SystemCounters.CounterType.MEMPOOL_CURRENT_SIZE),
            txns.size());
      }

      final var peers =
          peersView
              .peers()
              .map(PeersView.PeerInfo::bftNode)
              .collect(ImmutableList.toImmutableList());
      txns.forEach(
          txn -> {
            int index = random.nextInt(sendToSelf ? peers.size() + 1 : peers.size());
            var mempoolAdd = MempoolAdd.create(txn);
            if (index == peers.size()) {
              this.mempoolAddEventDispatcher.dispatch(mempoolAdd);
            } else {
              this.remoteMempoolAddEventDispatcher.dispatch(peers.get(index), mempoolAdd);
            }
          });

      mempoolFillDispatcher.dispatch(ScheduledMempoolFill.create(), 500);
    };
  }
}
