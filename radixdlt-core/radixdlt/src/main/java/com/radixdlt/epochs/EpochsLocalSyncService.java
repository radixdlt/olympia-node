/*
 * (C) Copyright 2021 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 */

package com.radixdlt.epochs;

import com.google.inject.Inject;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.epoch.EpochChange;
import com.radixdlt.environment.EventProcessor;
import com.radixdlt.environment.RemoteEventProcessor;
import com.radixdlt.sync.LocalSyncService;
import com.radixdlt.sync.messages.local.SyncLedgerUpdateTimeout;
import com.radixdlt.sync.validation.RemoteSyncResponseValidatorSetVerifier;
import com.radixdlt.sync.messages.local.LocalSyncRequest;
import javax.annotation.concurrent.NotThreadSafe;

import com.radixdlt.sync.messages.local.SyncCheckReceiveStatusTimeout;
import com.radixdlt.sync.messages.local.SyncCheckTrigger;
import com.radixdlt.sync.messages.local.SyncRequestTimeout;
import com.radixdlt.sync.messages.remote.StatusResponse;
import com.radixdlt.sync.messages.remote.SyncResponse;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Manages the syncing service across epochs
 */
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
		LocalSyncServiceFactory localSyncServiceFactory
	) {
		this.currentEpoch = initialEpoch;
		this.localSyncService = initialLocalSyncService;

		this.localSyncServiceFactory = localSyncServiceFactory;
	}

	public EventProcessor<EpochsLedgerUpdate> epochsLedgerUpdateEventProcessor() {
		return this::processLedgerUpdate;
	}

	private void processLedgerUpdate(EpochsLedgerUpdate ledgerUpdate) {
		ledgerUpdate.getEpochChange().ifPresentOrElse(
			epochChange -> {
				// epoch has changed, replace localSyncService (keep current state) and proceed
				this.currentEpoch = epochChange;

				this.localSyncService = localSyncServiceFactory.create(
					new RemoteSyncResponseValidatorSetVerifier(
						epochChange.getBFTConfiguration().getValidatorSet()
					),
					this.localSyncService.getSyncState()
				);

				this.localSyncService.ledgerUpdateEventProcessor().process(ledgerUpdate);
			},
			() -> {
				// epoch hasn't changed, all good, proceed
				this.localSyncService.ledgerUpdateEventProcessor().process(ledgerUpdate);
			}
		);
	}

	public EventProcessor<LocalSyncRequest> localSyncRequestEventProcessor() {
		return this::processLocalSyncRequest;
	}

	private void processLocalSyncRequest(LocalSyncRequest request) {
		final long targetEpoch = request.getTarget().getEpoch();

		if (targetEpoch < currentEpoch.getEpoch()) {
			log.trace("Request epoch {} is lower from current {} ignoring: {}", targetEpoch, currentEpoch.getEpoch(), request);
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

	public EventProcessor<SyncCheckReceiveStatusTimeout> syncCheckReceiveStatusTimeoutEventProcessor() {
		return this::processSyncCheckReceiveStatusTimeout;
	}

	private void processSyncCheckReceiveStatusTimeout(SyncCheckReceiveStatusTimeout syncCheckReceiveStatusTimeout) {
		this.localSyncService.syncCheckReceiveStatusTimeoutEventProcessor().process(syncCheckReceiveStatusTimeout);
	}

	public EventProcessor<SyncLedgerUpdateTimeout> syncLedgerUpdateTimeoutProcessor() {
		return this::processSyncLedgerUpdateTimeout;
	}

	private void processSyncLedgerUpdateTimeout(SyncLedgerUpdateTimeout syncLedgerUpdateTimeout) {
		this.localSyncService.syncLedgerUpdateTimeoutProcessor().process(syncLedgerUpdateTimeout);
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
}
