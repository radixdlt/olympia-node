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

package com.radixdlt.sync;

import com.google.common.collect.ImmutableList;
import com.google.inject.AbstractModule;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.multibindings.ProvidesIntoSet;
import com.radixdlt.atom.Txn;
import com.radixdlt.consensus.LedgerProof;
import com.radixdlt.environment.EventDispatcher;
import com.radixdlt.environment.EventProcessor;
import com.radixdlt.environment.EventProcessorOnDispatch;
import com.radixdlt.environment.EventProcessorOnRunner;
import com.radixdlt.environment.LocalEvents;
import com.radixdlt.environment.ProcessOnDispatch;
import com.radixdlt.environment.RemoteEventProcessorOnRunner;
import com.radixdlt.environment.Runners;
import com.radixdlt.ledger.LedgerUpdate;
import com.radixdlt.ledger.VerifiedTxnsAndProof;
import com.radixdlt.store.LastEpochProof;
import com.radixdlt.sync.messages.local.LocalSyncRequest;
import com.radixdlt.sync.messages.local.SyncCheckReceiveStatusTimeout;
import com.radixdlt.sync.messages.local.SyncCheckTrigger;
import com.radixdlt.sync.messages.local.SyncLedgerUpdateTimeout;
import com.radixdlt.sync.messages.local.SyncRequestTimeout;
import com.radixdlt.sync.messages.remote.LedgerStatusUpdate;
import com.radixdlt.sync.messages.remote.StatusRequest;
import com.radixdlt.sync.messages.remote.StatusResponse;
import com.radixdlt.sync.messages.remote.SyncRequest;
import com.radixdlt.sync.messages.remote.SyncResponse;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.LongStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class MockedSyncServiceModule extends AbstractModule {
  private static final Logger logger = LogManager.getLogger();

  private final ConcurrentMap<Long, Txn> sharedCommittedCommands;
  private final ConcurrentMap<Long, LedgerProof> sharedEpochProofs;

  public MockedSyncServiceModule() {
    this.sharedCommittedCommands = new ConcurrentHashMap<>();
    this.sharedEpochProofs = new ConcurrentHashMap<>();
  }

  @Override
  public void configure() {
    var eventBinder =
        Multibinder.newSetBinder(binder(), new TypeLiteral<Class<?>>() {}, LocalEvents.class)
            .permitDuplicates();
    eventBinder.addBinding().toInstance(SyncCheckTrigger.class);
    eventBinder.addBinding().toInstance(SyncCheckReceiveStatusTimeout.class);
    eventBinder.addBinding().toInstance(SyncRequestTimeout.class);
    eventBinder.addBinding().toInstance(LocalSyncRequest.class);
    eventBinder.addBinding().toInstance(SyncLedgerUpdateTimeout.class);
  }

  @Singleton
  @ProvidesIntoSet
  private EventProcessorOnDispatch<?> ledgerUpdateEventProcessor() {
    return new EventProcessorOnDispatch<>(
        LedgerUpdate.class,
        update -> {
          final LedgerProof headerAndProof = update.getTail();
          long stateVersion = headerAndProof.getAccumulatorState().getStateVersion();
          long firstVersion = stateVersion - update.getNewTxns().size() + 1;
          for (int i = 0; i < update.getNewTxns().size(); i++) {
            sharedCommittedCommands.put(firstVersion + i, update.getNewTxns().get(i));
          }

          if (update.getTail().isEndOfEpoch()) {
            logger.info("Epoch Proof: " + (update.getTail().getEpoch() + 1));
            sharedEpochProofs.put(update.getTail().getEpoch() + 1, update.getTail());
          }
        });
  }

  @ProvidesIntoSet
  @Singleton
  @ProcessOnDispatch
  EventProcessor<LocalSyncRequest> localSyncRequestEventProcessor(
      @LastEpochProof LedgerProof genesis,
      EventDispatcher<VerifiedTxnsAndProof> syncCommandsDispatcher) {
    return new EventProcessor<>() {
      long currentVersion = genesis.getStateVersion();
      long currentEpoch = genesis.getEpoch() + 1;

      private void syncTo(LedgerProof proof) {
        var txns =
            LongStream.range(currentVersion + 1, proof.getStateVersion() + 1)
                .mapToObj(sharedCommittedCommands::get)
                .collect(ImmutableList.toImmutableList());
        syncCommandsDispatcher.dispatch(VerifiedTxnsAndProof.create(txns, proof));
        currentVersion = proof.getStateVersion();
        if (proof.isEndOfEpoch()) {
          currentEpoch = proof.getEpoch() + 1;
        } else {
          currentEpoch = proof.getEpoch();
        }
      }

      @Override
      public void process(LocalSyncRequest request) {
        while (currentEpoch < request.getTarget().getEpoch()) {
          if (!sharedEpochProofs.containsKey(currentEpoch + 1)) {
            throw new IllegalStateException("Epoch proof does not exist: " + currentEpoch + 1);
          }

          syncTo(sharedEpochProofs.get(currentEpoch + 1));
        }

        syncTo(request.getTarget());

        final long targetVersion = request.getTarget().getStateVersion();
        var txns =
            LongStream.range(currentVersion + 1, targetVersion + 1)
                .mapToObj(sharedCommittedCommands::get)
                .collect(ImmutableList.toImmutableList());

        syncCommandsDispatcher.dispatch(VerifiedTxnsAndProof.create(txns, request.getTarget()));
        currentVersion = targetVersion;
        currentEpoch = request.getTarget().getEpoch();
      }
    };
  }

  @ProvidesIntoSet
  private RemoteEventProcessorOnRunner<?> ledgerStatusUpdateRemoteEventProcessor(
      EventDispatcher<LocalSyncRequest> localSyncRequestEventDispatcher) {
    return new RemoteEventProcessorOnRunner<>(
        Runners.SYNC,
        LedgerStatusUpdate.class,
        (sender, ev) ->
            localSyncRequestEventDispatcher.dispatch(
                new LocalSyncRequest(ev.getHeader(), ImmutableList.of(sender))));
  }

  @ProvidesIntoSet
  private EventProcessorOnRunner<?> epochsLedgerUpdateEventProcessor() {
    return noOpProcessor(LedgerUpdate.class);
  }

  @ProvidesIntoSet
  private EventProcessorOnRunner<?> syncCheckTriggerEventProcessor() {
    return noOpProcessor(SyncCheckTrigger.class);
  }

  @ProvidesIntoSet
  private EventProcessorOnRunner<?> syncCheckReceiveStatusTimeoutEventProcessor() {
    return noOpProcessor(SyncCheckReceiveStatusTimeout.class);
  }

  @ProvidesIntoSet
  private EventProcessorOnRunner<?> syncRequestTimeoutEventProcessor() {
    return noOpProcessor(SyncRequestTimeout.class);
  }

  @ProvidesIntoSet
  private EventProcessorOnRunner<?> localSyncRequestEventProcessor() {
    return noOpProcessor(LocalSyncRequest.class);
  }

  @ProvidesIntoSet
  private EventProcessorOnRunner<?> syncLedgerUpdateTimeoutEventProcessor() {
    return noOpProcessor(SyncLedgerUpdateTimeout.class);
  }

  @ProvidesIntoSet
  private RemoteEventProcessorOnRunner<?> statusRequestEventProcessor() {
    return noOpRemoteProcessor(StatusRequest.class);
  }

  @ProvidesIntoSet
  private RemoteEventProcessorOnRunner<?> statusResponseEventProcessor() {
    return noOpRemoteProcessor(StatusResponse.class);
  }

  @ProvidesIntoSet
  private RemoteEventProcessorOnRunner<?> syncRequestEventProcessor() {
    return noOpRemoteProcessor(SyncRequest.class);
  }

  @ProvidesIntoSet
  private RemoteEventProcessorOnRunner<?> syncResponseEventProcessor() {
    return noOpRemoteProcessor(SyncResponse.class);
  }

  private EventProcessorOnRunner<?> noOpProcessor(Class<?> clazz) {
    return new EventProcessorOnRunner<>(Runners.SYNC, clazz, ev -> {});
  }

  private RemoteEventProcessorOnRunner<?> noOpRemoteProcessor(Class<?> clazz) {
    return new RemoteEventProcessorOnRunner<>(Runners.SYNC, clazz, (sender, ev) -> {});
  }
}
