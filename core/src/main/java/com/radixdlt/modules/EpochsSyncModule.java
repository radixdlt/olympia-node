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

package com.radixdlt.modules;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.TypeLiteral;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.multibindings.ProvidesIntoSet;
import com.radixdlt.monitoring.SystemCounters;
import com.radixdlt.environment.EventProcessorOnRunner;
import com.radixdlt.environment.LocalEvents;
import com.radixdlt.environment.RemoteEventDispatcher;
import com.radixdlt.environment.RemoteEventProcessorOnRunner;
import com.radixdlt.environment.Runners;
import com.radixdlt.environment.ScheduledEventDispatcher;
import com.radixdlt.sync.epochs.EpochsLocalSyncService;
import com.radixdlt.sync.epochs.LocalSyncServiceFactory;
import com.radixdlt.ledger.AccumulatorState;
import com.radixdlt.ledger.LedgerAccumulatorVerifier;
import com.radixdlt.ledger.LedgerUpdate;
import com.radixdlt.network.p2p.PeersView;
import com.radixdlt.sync.LocalSyncService;
import com.radixdlt.sync.LocalSyncService.InvalidSyncResponseHandler;
import com.radixdlt.sync.LocalSyncService.VerifiedSyncResponseHandler;
import com.radixdlt.sync.RemoteSyncService;
import com.radixdlt.sync.SyncConfig;
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
import com.radixdlt.sync.validation.RemoteSyncResponseSignaturesVerifier;
import java.util.Comparator;

/** Epoch+Sync extension */
public class EpochsSyncModule extends AbstractModule {

  @Override
  public void configure() {
    bind(EpochsLocalSyncService.class).in(Scopes.SINGLETON);

    var eventBinder =
        Multibinder.newSetBinder(binder(), new TypeLiteral<Class<?>>() {}, LocalEvents.class)
            .permitDuplicates();
    eventBinder.addBinding().toInstance(SyncCheckTrigger.class);
    eventBinder.addBinding().toInstance(SyncCheckReceiveStatusTimeout.class);
    eventBinder.addBinding().toInstance(SyncRequestTimeout.class);
    eventBinder.addBinding().toInstance(LocalSyncRequest.class);
    eventBinder.addBinding().toInstance(SyncLedgerUpdateTimeout.class);
  }

  @ProvidesIntoSet
  private EventProcessorOnRunner<?> epochsLedgerUpdateEventProcessorLocalSync(
      EpochsLocalSyncService epochsLocalSyncService) {
    return new EventProcessorOnRunner<>(
        Runners.SYNC,
        LedgerUpdate.class,
        epochsLocalSyncService.epochsLedgerUpdateEventProcessor());
  }

  @ProvidesIntoSet
  private EventProcessorOnRunner<?> ledgerUpdateEventProcessorRemoteSync(
      RemoteSyncService remoteSyncService) {
    return new EventProcessorOnRunner<>(
        Runners.SYNC,
        LedgerUpdate.class,
        update -> remoteSyncService.ledgerUpdateEventProcessor().process(update));
  }

  @ProvidesIntoSet
  private EventProcessorOnRunner<?> syncCheckTriggerEventProcessor(
      EpochsLocalSyncService epochsLocalSyncService) {
    return new EventProcessorOnRunner<>(
        Runners.SYNC,
        SyncCheckTrigger.class,
        epochsLocalSyncService.syncCheckTriggerEventProcessor());
  }

  @ProvidesIntoSet
  private EventProcessorOnRunner<?> syncCheckReceiveStatusTimeoutEventProcessor(
      EpochsLocalSyncService epochsLocalSyncService) {
    return new EventProcessorOnRunner<>(
        Runners.SYNC,
        SyncCheckReceiveStatusTimeout.class,
        epochsLocalSyncService.syncCheckReceiveStatusTimeoutEventProcessor());
  }

  @ProvidesIntoSet
  private EventProcessorOnRunner<?> syncRequestTimeoutEventProcessor(
      EpochsLocalSyncService epochsLocalSyncService) {
    return new EventProcessorOnRunner<>(
        Runners.SYNC,
        SyncRequestTimeout.class,
        epochsLocalSyncService.syncRequestTimeoutEventProcessor());
  }

  @ProvidesIntoSet
  private EventProcessorOnRunner<?> syncLedgerUpdateTimeoutProcessor(
      EpochsLocalSyncService epochsLocalSyncService) {
    return new EventProcessorOnRunner<>(
        Runners.SYNC,
        SyncLedgerUpdateTimeout.class,
        epochsLocalSyncService.syncLedgerUpdateTimeoutProcessor());
  }

  @ProvidesIntoSet
  private EventProcessorOnRunner<?> localSyncRequestEventProcessor(
      EpochsLocalSyncService epochsLocalSyncService) {
    return new EventProcessorOnRunner<>(
        Runners.SYNC,
        LocalSyncRequest.class,
        epochsLocalSyncService.localSyncRequestEventProcessor());
  }

  @ProvidesIntoSet
  private RemoteEventProcessorOnRunner<?> statusResponseEventProcessor(
      EpochsLocalSyncService epochsLocalSyncService) {
    return new RemoteEventProcessorOnRunner<>(
        Runners.SYNC, StatusResponse.class, epochsLocalSyncService.statusResponseEventProcessor());
  }

  @ProvidesIntoSet
  private RemoteEventProcessorOnRunner<?> syncResponseEventProcessor(
      EpochsLocalSyncService epochsLocalSyncService) {
    return new RemoteEventProcessorOnRunner<>(
        Runners.SYNC, SyncResponse.class, epochsLocalSyncService.syncResponseEventProcessor());
  }

  @ProvidesIntoSet
  private RemoteEventProcessorOnRunner<?> ledgerStatusUpdateEventProcessor(
      EpochsLocalSyncService epochsLocalSyncService) {
    return new RemoteEventProcessorOnRunner<>(
        Runners.SYNC,
        LedgerStatusUpdate.class,
        epochsLocalSyncService.ledgerStatusUpdateEventProcessor());
  }

  @Provides
  private LocalSyncServiceFactory localSyncServiceFactory(
      RemoteEventDispatcher<StatusRequest> statusRequestDispatcher,
      ScheduledEventDispatcher<SyncCheckReceiveStatusTimeout>
          syncCheckReceiveStatusTimeoutDispatcher,
      RemoteEventDispatcher<SyncRequest> syncRequestDispatcher,
      ScheduledEventDispatcher<SyncRequestTimeout> syncRequestTimeoutDispatcher,
      ScheduledEventDispatcher<SyncLedgerUpdateTimeout> syncLedgerUpdateTimeoutDispatcher,
      SyncConfig syncConfig,
      SystemCounters systemCounters,
      PeersView peersView,
      Comparator<AccumulatorState> accComparator,
      RemoteSyncResponseSignaturesVerifier signaturesVerifier,
      LedgerAccumulatorVerifier accumulatorVerifier,
      VerifiedSyncResponseHandler verifiedSyncResponseHandler,
      InvalidSyncResponseHandler invalidSyncResponseHandler) {
    return (remoteSyncResponseValidatorSetVerifier, syncState) ->
        new LocalSyncService(
            statusRequestDispatcher,
            syncCheckReceiveStatusTimeoutDispatcher,
            syncRequestDispatcher,
            syncRequestTimeoutDispatcher,
            syncLedgerUpdateTimeoutDispatcher,
            syncConfig,
            systemCounters,
            peersView,
            accComparator,
            remoteSyncResponseValidatorSetVerifier,
            signaturesVerifier,
            accumulatorVerifier,
            verifiedSyncResponseHandler,
            invalidSyncResponseHandler,
            syncState);
  }
}
