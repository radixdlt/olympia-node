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

import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.radixdlt.consensus.BFTConfiguration;
import com.radixdlt.consensus.VerifiedLedgerHeaderAndProof;
import com.radixdlt.consensus.epoch.EpochChange;
import com.radixdlt.ledger.DtoCommandsAndProof;
import com.radixdlt.ledger.DtoLedgerHeaderAndProof;
import com.radixdlt.ledger.LedgerUpdate;
import com.radixdlt.sync.LocalSyncServiceAccumulatorProcessor.SyncInProgress;
import com.radixdlt.sync.LocalSyncRequest;
import com.radixdlt.sync.LocalSyncServiceProcessor;
import com.radixdlt.sync.RemoteSyncResponseProcessor;
import com.radixdlt.sync.RemoteSyncResponseValidatorSetVerifier;
import com.radixdlt.sync.StateSyncNetwork;
import com.radixdlt.sync.RemoteSyncResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.TreeMap;
import java.util.function.Function;
import javax.annotation.concurrent.NotThreadSafe;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Manages the syncing service across epochs
 */
@Singleton
@NotThreadSafe
public class EpochsLocalSyncServiceProcessor implements LocalSyncServiceProcessor<EpochsLedgerUpdate>, RemoteSyncResponseProcessor {
	private static final Logger log = LogManager.getLogger();

	public interface SyncedEpochSender {
		void sendSyncedEpoch(VerifiedLedgerHeaderAndProof headerAndProof);
	}

	private final Function<BFTConfiguration, LocalSyncServiceProcessor<LedgerUpdate>> localSyncFactory;
	private final Function<BFTConfiguration, RemoteSyncResponseValidatorSetVerifier> verifierFactory;
	private final SyncedEpochSender syncedEpochSender;
	private final StateSyncNetwork stateSyncNetwork;
	private final TreeMap<Long, List<LocalSyncRequest>> outsideOfCurrentEpochRequests = new TreeMap<>();

	private RemoteSyncResponseProcessor currentVerifier;
	private EpochChange currentEpoch;
	private VerifiedLedgerHeaderAndProof currentHeader;
	private LocalSyncServiceProcessor<LedgerUpdate> localSyncServiceProcessor;

	@Inject
	public EpochsLocalSyncServiceProcessor(
		LocalSyncServiceProcessor<LedgerUpdate> initialProcessor,
		RemoteSyncResponseValidatorSetVerifier initialVerifier,
		EpochChange initialEpoch,
		VerifiedLedgerHeaderAndProof currentHeader,
		Function<BFTConfiguration, LocalSyncServiceProcessor<LedgerUpdate>> localSyncFactory,
		Function<BFTConfiguration, RemoteSyncResponseValidatorSetVerifier> verifierFactory,
		StateSyncNetwork stateSyncNetwork,
		SyncedEpochSender syncedEpochSender
	) {
		this.currentEpoch = initialEpoch;
		this.currentHeader = currentHeader;
		this.currentVerifier = initialVerifier;

		this.localSyncFactory = localSyncFactory;
		this.verifierFactory = verifierFactory;
		this.localSyncServiceProcessor = initialProcessor;
		this.syncedEpochSender = syncedEpochSender;
		this.stateSyncNetwork = stateSyncNetwork;
	}

	@Override
	public void processLedgerUpdate(EpochsLedgerUpdate ledgerUpdate) {
		if (ledgerUpdate.getEpochChange().isPresent()) {
			final EpochChange epochChange = ledgerUpdate.getEpochChange().get();
			this.currentEpoch = epochChange;
			this.currentHeader = ledgerUpdate.getTail();
			this.localSyncServiceProcessor = localSyncFactory.apply(epochChange.getBFTConfiguration());
			this.currentVerifier = verifierFactory.apply(epochChange.getBFTConfiguration());
			this.outsideOfCurrentEpochRequests.headMap(epochChange.getEpoch()).clear();

			// TODO: Cleanup further requests
			this.outsideOfCurrentEpochRequests.values().stream().flatMap(List::stream)
				.findFirst()
				.ifPresent(request -> {
					log.info("Epoch updated sending further sync requests to {}", request.getTargetNodes().get(0));
					stateSyncNetwork.sendSyncRequest(request.getTargetNodes().get(0), currentEpoch.getProof().toDto());
				});
		} else {
			this.localSyncServiceProcessor.processLedgerUpdate(ledgerUpdate);
		}
	}

	@Override
	public void processLocalSyncRequest(LocalSyncRequest request) {
		if (request.getTarget().getEpoch() > currentEpoch.getEpoch()) {
			outsideOfCurrentEpochRequests.putIfAbsent(request.getTarget().getEpoch(), new ArrayList<>());
			outsideOfCurrentEpochRequests.get(request.getTarget().getEpoch()).add(request);
			log.warn("Request {} is a different epoch from current {} sending epoch sync", request, currentEpoch.getEpoch());
			stateSyncNetwork.sendSyncRequest(request.getTargetNodes().get(0), currentEpoch.getProof().toDto());
			return;
		}

		if (Objects.equals(request.getTarget().getAccumulatorState(), this.currentHeader.getAccumulatorState())) {
			if (!this.currentHeader.isEndOfEpoch() && request.getTarget().isEndOfEpoch()) {
				syncedEpochSender.sendSyncedEpoch(request.getTarget());
			}
			return;
		}

		localSyncServiceProcessor.processLocalSyncRequest(request);
	}

	@Override
	public void processSyncTimeout(SyncInProgress timeout) {
		localSyncServiceProcessor.processSyncTimeout(timeout);
	}

	@Override
	public void processSyncResponse(RemoteSyncResponse syncResponse) {
		DtoCommandsAndProof dtoCommandsAndProof = syncResponse.getCommandsAndProof();
		if (dtoCommandsAndProof.getTail().getLedgerHeader().getEpoch() != currentEpoch.getEpoch()) {
			log.warn("Response {} is a different epoch from current {}", syncResponse, currentEpoch.getEpoch());
			return;
		}

		if (Objects.equals(dtoCommandsAndProof.getHead().getLedgerHeader(), this.currentEpoch.getProof().getRaw())) {
			log.info("Received response to next epoch sync current {} next {}", this.currentEpoch, dtoCommandsAndProof);
			DtoLedgerHeaderAndProof dto = dtoCommandsAndProof.getTail();
			if (!dto.getLedgerHeader().isEndOfEpoch()) {
				log.warn("Bad message: {}", syncResponse);
				return;
			}

			// TODO: verify
			VerifiedLedgerHeaderAndProof verified = new VerifiedLedgerHeaderAndProof(
				dto.getOpaque0(),
				dto.getOpaque1(),
				dto.getOpaque2(),
				dto.getOpaque3(),
				dto.getLedgerHeader(),
				dto.getSignatures()
			);

			if (Objects.equals(dto.getLedgerHeader().getAccumulatorState(), this.currentHeader.getAccumulatorState())) {
				syncedEpochSender.sendSyncedEpoch(verified);
				return;
			}

			LocalSyncRequest localSyncRequest = new LocalSyncRequest(verified, ImmutableList.of(syncResponse.getSender()));
			localSyncServiceProcessor.processLocalSyncRequest(localSyncRequest);

			return;
		}

		currentVerifier.processSyncResponse(syncResponse);
	}
}
