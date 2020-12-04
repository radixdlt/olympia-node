/*
 * (C) Copyright 2020 Radix DLT Ltd
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

import com.google.common.collect.Lists;
import com.google.inject.Inject;
import com.radixdlt.consensus.BFTConfiguration;
import com.radixdlt.consensus.VerifiedLedgerHeaderAndProof;
import com.radixdlt.consensus.epoch.EpochChange;
import com.radixdlt.environment.EventProcessor;
import com.radixdlt.environment.RemoteEventDispatcher;
import com.radixdlt.ledger.DtoLedgerHeaderAndProof;
import com.radixdlt.store.LastProof;
import com.radixdlt.sync.LocalSyncServiceAccumulatorProcessor;
import com.radixdlt.sync.LocalSyncServiceAccumulatorProcessor.SyncInProgress;
import com.radixdlt.sync.LocalSyncRequest;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Function;
import javax.annotation.concurrent.NotThreadSafe;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Manages the syncing service across epochs
 */
@NotThreadSafe
public class EpochsLocalSyncServiceProcessor {
	private static final Logger log = LogManager.getLogger();

	private final Function<BFTConfiguration, LocalSyncServiceAccumulatorProcessor> localSyncFactory;
	private final SyncedEpochSender syncedEpochSender;
	private final RemoteEventDispatcher<DtoLedgerHeaderAndProof> requestDispatcher;
	private final TreeMap<Long, List<LocalSyncRequest>> outsideOfCurrentEpochRequests = new TreeMap<>();

	private EpochChange currentEpoch;
	private VerifiedLedgerHeaderAndProof currentHeader;
	private LocalSyncServiceAccumulatorProcessor localSyncServiceProcessor;

	@Inject
	public EpochsLocalSyncServiceProcessor(
		LocalSyncServiceAccumulatorProcessor initialProcessor,
		EpochChange initialEpoch,
		@LastProof VerifiedLedgerHeaderAndProof initialHeader,
		Function<BFTConfiguration, LocalSyncServiceAccumulatorProcessor> localSyncFactory,
		RemoteEventDispatcher<DtoLedgerHeaderAndProof> requestDispatcher,
		SyncedEpochSender syncedEpochSender
	) {
		this.currentEpoch = initialEpoch;
		this.currentHeader = initialHeader;
		this.localSyncServiceProcessor = initialProcessor;

		this.localSyncFactory = localSyncFactory;
		this.syncedEpochSender = syncedEpochSender;
		this.requestDispatcher = Objects.requireNonNull(requestDispatcher);
	}

	private void processLedgerUpdate(EpochsLedgerUpdate ledgerUpdate) {
		Optional<EpochChange> maybeEpochChange = ledgerUpdate.getEpochChange();
		if (maybeEpochChange.isPresent()) {
			final EpochChange epochChange = maybeEpochChange.get();
			this.currentEpoch = epochChange;
			this.currentHeader = epochChange.getBFTConfiguration().getGenesisHeader();
			this.localSyncServiceProcessor = localSyncFactory.apply(epochChange.getBFTConfiguration());

			// TODO: Cleanup further requests
			this.outsideOfCurrentEpochRequests.headMap(epochChange.getEpoch()).clear();
			this.outsideOfCurrentEpochRequests.values().stream().flatMap(List::stream)
				.findFirst()
				.ifPresent(request -> {
					log.info("Epoch updated sending further sync requests to {}", request.getTargetNodes().get(0));
					requestDispatcher.dispatch(request.getTargetNodes().get(0), currentEpoch.getProof().toDto());
				});
		} else {
			this.currentHeader = ledgerUpdate.getTail();
			this.localSyncServiceProcessor.processLedgerUpdate(ledgerUpdate);
		}
	}

	public EventProcessor<EpochsLedgerUpdate> epochsLedgerUpdateEventProcessor() {
		return this::processLedgerUpdate;
	}

	public EventProcessor<LocalSyncRequest> localSyncRequestEventProcessor() {
		return this::processLocalSyncRequest;
	}

	private void processLocalSyncRequest(LocalSyncRequest request) {
		final long targetEpoch = request.getTarget().getEpoch();
		if (targetEpoch > currentEpoch.getEpoch()) {
			log.warn("Request {} is a different epoch from current {} sending epoch sync", request, currentEpoch.getEpoch());

			outsideOfCurrentEpochRequests.computeIfAbsent(targetEpoch, epoch -> Lists.newArrayList()).add(request);
			requestDispatcher.dispatch(request.getTargetNodes().get(0), currentEpoch.getProof().toDto());
			return;
		}

		if (targetEpoch < currentEpoch.getEpoch()) {
			log.trace("Request epoch {} is lower from current {} ignoring: {}", targetEpoch, currentEpoch.getEpoch(), request);
			return;
		}

		if (Objects.equals(request.getTarget().getAccumulatorState(), this.currentHeader.getAccumulatorState())) {
			if (request.getTarget().isEndOfEpoch()) {
				syncedEpochSender.sendSyncedEpoch(request.getTarget());
			}
			return;
		}

		localSyncServiceProcessor.localSyncRequestEventProcessor().process(request);
	}

	public EventProcessor<SyncInProgress> syncTimeoutProcessor() {
		return syncTimeout -> {
			if (syncTimeout.getTargetHeader().getEpoch() == this.currentEpoch.getEpoch()) {
				localSyncServiceProcessor.syncTimeoutProcessor().process(syncTimeout);
			}
		};
	}
}
