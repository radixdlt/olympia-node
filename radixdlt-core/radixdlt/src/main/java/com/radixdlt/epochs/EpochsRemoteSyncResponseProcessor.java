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
import com.radixdlt.consensus.VerifiedLedgerHeaderAndProof;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.epoch.EpochChange;
import com.radixdlt.environment.EventDispatcher;
import com.radixdlt.environment.EventProcessor;
import com.radixdlt.environment.RemoteEventProcessor;
import com.radixdlt.ledger.DtoCommandsAndProof;
import com.radixdlt.ledger.DtoLedgerHeaderAndProof;
import com.radixdlt.store.LastEpochProof;
import com.radixdlt.sync.LocalSyncRequest;
import com.radixdlt.sync.RemoteSyncResponseValidatorSetVerifier;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import javax.annotation.concurrent.NotThreadSafe;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Processes remote sync responses with the current epoch
 * in mind.
 */
@NotThreadSafe
public final class EpochsRemoteSyncResponseProcessor {
	private static final Logger log = LogManager.getLogger();

	private final Function<EpochChange, RemoteSyncResponseValidatorSetVerifier> verifierFactory;
	private final EventDispatcher<LocalSyncRequest> localSyncRequestProcessor;

	private RemoteSyncResponseValidatorSetVerifier currentVerifier;
	private EpochChange currentEpoch;
	private VerifiedLedgerHeaderAndProof currentEpochProof;

	@Inject
	public EpochsRemoteSyncResponseProcessor(
		EventDispatcher<LocalSyncRequest> localSyncRequestProcessor,
		RemoteSyncResponseValidatorSetVerifier initialVerifier,
		EpochChange initialEpoch,
		@LastEpochProof VerifiedLedgerHeaderAndProof currentEpochProof,
		Function<EpochChange, RemoteSyncResponseValidatorSetVerifier> verifierFactory
	) {
		this.localSyncRequestProcessor = Objects.requireNonNull(localSyncRequestProcessor);
		this.currentEpoch = Objects.requireNonNull(initialEpoch);
		this.currentEpochProof = Objects.requireNonNull(currentEpochProof);
		this.currentVerifier = Objects.requireNonNull(initialVerifier);
		this.verifierFactory = Objects.requireNonNull(verifierFactory);
	}

	private void processLedgerUpdate(EpochsLedgerUpdate ledgerUpdate) {
		Optional<EpochChange> maybeEpochChange = ledgerUpdate.getEpochChange();
		if (maybeEpochChange.isPresent()) {
			final EpochChange epochChange = maybeEpochChange.get();
			this.currentEpoch = epochChange;
			this.currentEpochProof = ledgerUpdate.getTail();
			this.currentVerifier = verifierFactory.apply(epochChange);
		}
	}

	public EventProcessor<EpochsLedgerUpdate> epochsLedgerUpdateEventProcessor() {
		return this::processLedgerUpdate;
	}

	public RemoteEventProcessor<DtoCommandsAndProof> syncResponseProcessor() {
		return this::processSyncResponse;
	}

	private void processSyncResponse(BFTNode sender, DtoCommandsAndProof dtoCommandsAndProof) {
		if (dtoCommandsAndProof.getTail().getLedgerHeader().getEpoch() != currentEpoch.getEpoch()) {
			log.warn("Response {} is a different epoch from current {}", dtoCommandsAndProof, currentEpoch.getEpoch());
			return;
		}

		if (Objects.equals(dtoCommandsAndProof.getHead().getLedgerHeader(), this.currentEpoch.getProof().getRaw())) {
			log.debug("Received response to next epoch sync current {} next {}", this.currentEpoch, dtoCommandsAndProof);
			DtoLedgerHeaderAndProof dto = dtoCommandsAndProof.getTail();
			if (!dto.getLedgerHeader().isEndOfEpoch()) {
				log.warn("Bad message as sync epoch responses must be end of epochs: {}", dtoCommandsAndProof);
				return;
			}

			if (Objects.equals(dto.getLedgerHeader().getAccumulatorState(), this.currentEpochProof.getAccumulatorState())) {
				// TODO: cleanup this mess
				DtoCommandsAndProof mockedDtoCommandsAndProof = new DtoCommandsAndProof(
					ImmutableList.of(),
					this.currentEpochProof.toDto(),
					dto
				);

				currentVerifier.process(sender, mockedDtoCommandsAndProof);
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
			LocalSyncRequest localSyncRequest = new LocalSyncRequest(verified, ImmutableList.of(sender));
			localSyncRequestProcessor.dispatch(localSyncRequest);
			return;
		}

		currentVerifier.process(sender, dtoCommandsAndProof);
	}
}
