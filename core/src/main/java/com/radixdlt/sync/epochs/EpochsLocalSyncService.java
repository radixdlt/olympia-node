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

package com.radixdlt.sync.epochs;

import com.google.inject.Inject;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.epoch.EpochChange;
import com.radixdlt.environment.EventProcessor;
import com.radixdlt.environment.RemoteEventProcessor;
import com.radixdlt.ledger.LedgerUpdate;
import com.radixdlt.sync.LocalSyncService;
import com.radixdlt.sync.messages.local.LocalSyncRequest;
import com.radixdlt.sync.messages.local.SyncCheckReceiveStatusTimeout;
import com.radixdlt.sync.messages.local.SyncCheckTrigger;
import com.radixdlt.sync.messages.local.SyncLedgerUpdateTimeout;
import com.radixdlt.sync.messages.local.SyncRequestTimeout;
import com.radixdlt.sync.messages.remote.LedgerStatusUpdate;
import com.radixdlt.sync.messages.remote.StatusResponse;
import com.radixdlt.sync.messages.remote.SyncResponse;
import com.radixdlt.sync.validation.RemoteSyncResponseValidatorSetVerifier;
import javax.annotation.concurrent.NotThreadSafe;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** Manages the syncing service across epochs */
@NotThreadSafe
public class EpochsLocalSyncService {
  private static final Logger log = LogManager.getLogger();

  private final LocalSyncServiceFactory localSyncServiceFactory;

  private EpochChange currentEpoch;
  private LocalSyncService localSyncService;

  @Inject
  public EpochsLocalSyncService(
      LocalSyncService initialLocalSyncService,
      EpochChange initialEpoch,
      LocalSyncServiceFactory localSyncServiceFactory) {
    this.currentEpoch = initialEpoch;
    this.localSyncService = initialLocalSyncService;

    this.localSyncServiceFactory = localSyncServiceFactory;
  }

  public EventProcessor<LedgerUpdate> epochsLedgerUpdateEventProcessor() {
    return this::processLedgerUpdate;
  }

  private void processLedgerUpdate(LedgerUpdate ledgerUpdate) {
    var epochChange = ledgerUpdate.getStateComputerOutput().getInstance(EpochChange.class);
    if (epochChange != null) {
      this.currentEpoch = epochChange;

      this.localSyncService =
          localSyncServiceFactory.create(
              new RemoteSyncResponseValidatorSetVerifier(
                  epochChange.getBFTConfiguration().getValidatorSet()),
              this.localSyncService.getSyncState());
    }
    this.localSyncService.ledgerUpdateEventProcessor().process(ledgerUpdate);
  }

  public EventProcessor<LocalSyncRequest> localSyncRequestEventProcessor() {
    return this::processLocalSyncRequest;
  }

  private void processLocalSyncRequest(LocalSyncRequest request) {
    final long targetEpoch = request.getTarget().getEpoch();

    if (targetEpoch < currentEpoch.getEpoch()) {
      log.trace(
          "Request epoch {} is lower from current {} ignoring: {}",
          targetEpoch,
          currentEpoch.getEpoch(),
          request);
      return;
    }

    localSyncService.localSyncRequestEventProcessor().process(request);
  }

  public EventProcessor<SyncCheckTrigger> syncCheckTriggerEventProcessor() {
    return this::processSyncCheckTrigger;
  }

  private void processSyncCheckTrigger(SyncCheckTrigger syncCheckTrigger) {
    this.localSyncService.syncCheckTriggerEventProcessor().process(syncCheckTrigger);
  }

  public EventProcessor<SyncCheckReceiveStatusTimeout>
      syncCheckReceiveStatusTimeoutEventProcessor() {
    return this::processSyncCheckReceiveStatusTimeout;
  }

  public EventProcessor<SyncLedgerUpdateTimeout> syncLedgerUpdateTimeoutProcessor() {
    return this::processSyncLedgerUpdateTimeout;
  }

  private void processSyncLedgerUpdateTimeout(SyncLedgerUpdateTimeout syncLedgerUpdateTimeout) {
    this.localSyncService.syncLedgerUpdateTimeoutProcessor().process(syncLedgerUpdateTimeout);
  }

  private void processSyncCheckReceiveStatusTimeout(
      SyncCheckReceiveStatusTimeout syncCheckReceiveStatusTimeout) {
    this.localSyncService
        .syncCheckReceiveStatusTimeoutEventProcessor()
        .process(syncCheckReceiveStatusTimeout);
  }

  public EventProcessor<SyncRequestTimeout> syncRequestTimeoutEventProcessor() {
    return this::processSyncRequestTimeout;
  }

  private void processSyncRequestTimeout(SyncRequestTimeout syncRequestTimeout) {
    this.localSyncService.syncRequestTimeoutEventProcessor().process(syncRequestTimeout);
  }

  public RemoteEventProcessor<StatusResponse> statusResponseEventProcessor() {
    return this::processStatusResponse;
  }

  private void processStatusResponse(BFTNode sender, StatusResponse statusResponse) {
    this.localSyncService.statusResponseEventProcessor().process(sender, statusResponse);
  }

  public RemoteEventProcessor<SyncResponse> syncResponseEventProcessor() {
    return this::processSyncResponse;
  }

  private void processSyncResponse(BFTNode sender, SyncResponse syncResponse) {
    this.localSyncService.syncResponseEventProcessor().process(sender, syncResponse);
  }

  public RemoteEventProcessor<LedgerStatusUpdate> ledgerStatusUpdateEventProcessor() {
    return this::processLedgerStatusUpdate;
  }

  private void processLedgerStatusUpdate(BFTNode sender, LedgerStatusUpdate ledgerStatusUpdate) {
    this.localSyncService.ledgerStatusUpdateEventProcessor().process(sender, ledgerStatusUpdate);
  }
}
