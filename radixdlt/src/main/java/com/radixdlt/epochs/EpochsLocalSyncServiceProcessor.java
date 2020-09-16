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

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.radixdlt.consensus.BFTConfiguration;
import com.radixdlt.consensus.VerifiedLedgerHeaderAndProof;
import com.radixdlt.consensus.epoch.EpochChange;
import com.radixdlt.ledger.DtoCommandsAndProof;
import com.radixdlt.ledger.LedgerUpdate;
import com.radixdlt.sync.BaseLocalSyncServiceProcessor.SyncInProgress;
import com.radixdlt.sync.LocalSyncRequest;
import com.radixdlt.sync.LocalSyncServiceProcessor;
import java.util.function.Function;
import javax.annotation.concurrent.NotThreadSafe;

@Singleton
@NotThreadSafe
public class EpochsLocalSyncServiceProcessor implements LocalSyncServiceProcessor<EpochsLedgerUpdate> {
	private final Function<BFTConfiguration, LocalSyncServiceProcessor<LedgerUpdate>> localSyncFactory;
	private EpochChange currentEpoch;
	private VerifiedLedgerHeaderAndProof currentHeader;
	private LocalSyncServiceProcessor<LedgerUpdate> localSyncServiceProcessor;

	@Inject
	public EpochsLocalSyncServiceProcessor(
		LocalSyncServiceProcessor<LedgerUpdate> initialProcessor,
		EpochChange initialEpoch,
		VerifiedLedgerHeaderAndProof initialHeader,
		Function<BFTConfiguration, LocalSyncServiceProcessor<LedgerUpdate>> localSyncFactory
	) {
		this.currentEpoch = initialEpoch;
		this.currentHeader = initialHeader;
		this.localSyncFactory = localSyncFactory;
		this.localSyncServiceProcessor = initialProcessor;
	}

	@Override
	public void processLedgerUpdate(EpochsLedgerUpdate ledgerUpdate) {
		this.currentHeader = ledgerUpdate.getTail();

		if (ledgerUpdate.getEpochChange().isPresent()) {
			final EpochChange epochChange = ledgerUpdate.getEpochChange().get();
			this.currentEpoch = epochChange;
			this.localSyncServiceProcessor = localSyncFactory.apply(epochChange.getBFTConfiguration());
		} else {
			this.localSyncServiceProcessor.processLedgerUpdate(ledgerUpdate);
		}
	}

	@Override
	public void processLocalSyncRequest(LocalSyncRequest request) {
		localSyncServiceProcessor.processLocalSyncRequest(request);
	}

	@Override
	public void processSyncTimeout(SyncInProgress timeout) {
		localSyncServiceProcessor.processSyncTimeout(timeout);
	}

	@Override
	public void processSyncResponse(DtoCommandsAndProof dtoCommandsAndProof) {
		if (dtoCommandsAndProof.getStartHeader().getLedgerHeader().getEpoch() != currentEpoch.getEpoch()
			|| dtoCommandsAndProof.getEndHeader().getLedgerHeader().getEpoch() != currentEpoch.getEpoch()) {
			return;
		}

		localSyncServiceProcessor.processSyncResponse(dtoCommandsAndProof);
	}
}
