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

package com.radixdlt.sync;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.radixdlt.consensus.BFTConfiguration;
import com.radixdlt.consensus.VerifiedLedgerHeaderAndProof;
import com.radixdlt.consensus.epoch.EpochChange;
import com.radixdlt.ledger.DtoCommandsAndProof;
import com.radixdlt.sync.LocalSyncServiceProcessor.SyncInProgress;
import java.util.function.Function;

@Singleton
public class EpochSyncServiceProcessor {
	private final Function<BFTConfiguration, LocalSyncServiceProcessor> localSyncFactory;
	private EpochChange currentEpoch;
	private LocalSyncServiceProcessor localSyncServiceProcessor;

	@Inject
	public EpochSyncServiceProcessor(
		EpochChange initialEpoch,
		Function<BFTConfiguration, LocalSyncServiceProcessor> localSyncFactory
	) {
		this.currentEpoch = initialEpoch;
		this.localSyncFactory = localSyncFactory;
	}

	public void start() {
		localSyncServiceProcessor = localSyncFactory.apply(currentEpoch.getBFTConfiguration());
	}

	public void processEpochChange(EpochChange epochChange) {
		localSyncServiceProcessor = localSyncFactory.apply(epochChange.getBFTConfiguration());
	}

	public void processVersionUpdate(VerifiedLedgerHeaderAndProof header) {
		localSyncServiceProcessor.processVersionUpdate(header);
	}

	public void processLocalSyncRequest(LocalSyncRequest request) {
		localSyncServiceProcessor.processLocalSyncRequest(request);
	}

	public void processSyncTimeout(SyncInProgress timeout) {
		localSyncServiceProcessor.processSyncTimeout(timeout);
	}

	public void processSyncResponse(DtoCommandsAndProof dtoCommandsAndProof) {
		if (dtoCommandsAndProof.getStartHeader().getLedgerHeader().getEpoch() != currentEpoch.getEpoch()
			|| dtoCommandsAndProof.getEndHeader().getLedgerHeader().getEpoch() != currentEpoch.getEpoch()) {
			return;
		}

		localSyncServiceProcessor.processSyncResponse(dtoCommandsAndProof);
	}
}
