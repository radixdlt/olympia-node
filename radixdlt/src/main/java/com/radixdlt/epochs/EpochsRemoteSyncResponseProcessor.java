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
import com.radixdlt.consensus.sync.SyncRequestSender;
import com.radixdlt.ledger.DtoCommandsAndProof;
import com.radixdlt.ledger.DtoLedgerHeaderAndProof;
import com.radixdlt.sync.LocalSyncRequest;
import com.radixdlt.sync.RemoteSyncResponse;
import com.radixdlt.sync.RemoteSyncResponseProcessor;
import com.radixdlt.sync.RemoteSyncResponseValidatorSetVerifier;
import java.util.Objects;
import java.util.function.Function;
import javax.annotation.concurrent.NotThreadSafe;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Singleton
@NotThreadSafe
public class EpochsRemoteSyncResponseProcessor implements RemoteSyncResponseProcessor {
	private static final Logger log = LogManager.getLogger();

	private final Function<BFTConfiguration, RemoteSyncResponseValidatorSetVerifier> verifierFactory;
	private final SyncedEpochSender syncedEpochSender;
	private final SyncRequestSender localSyncRequestSender;

	private RemoteSyncResponseValidatorSetVerifier currentVerifier;
	private EpochChange currentEpoch;
	private VerifiedLedgerHeaderAndProof currentHeader;

	@Inject
	public EpochsRemoteSyncResponseProcessor(
		SyncRequestSender localSyncRequestSender,
		RemoteSyncResponseValidatorSetVerifier initialVerifier,
		EpochChange initialEpoch,
		VerifiedLedgerHeaderAndProof currentHeader,
		Function<BFTConfiguration, RemoteSyncResponseValidatorSetVerifier> verifierFactory,
		SyncedEpochSender syncedEpochSender
	) {
		this.localSyncRequestSender = localSyncRequestSender;
		this.currentEpoch = initialEpoch;
		this.currentHeader = currentHeader;
		this.currentVerifier = initialVerifier;

		this.verifierFactory = verifierFactory;
		this.syncedEpochSender = syncedEpochSender;
	}


	public void processLedgerUpdate(EpochsLedgerUpdate ledgerUpdate) {
		if (ledgerUpdate.getEpochChange().isPresent()) {
			final EpochChange epochChange = ledgerUpdate.getEpochChange().get();
			this.currentEpoch = epochChange;
			this.currentHeader = ledgerUpdate.getTail();
			this.currentVerifier = verifierFactory.apply(epochChange.getBFTConfiguration());
		}
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
			localSyncRequestSender.sendLocalSyncRequest(localSyncRequest);

			return;
		}

		currentVerifier.processSyncResponse(syncResponse);
	}
}
