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

package com.radixdlt.systeminfo;

import com.google.inject.Inject;
import com.radixdlt.consensus.LedgerProof;
import com.radixdlt.consensus.QuorumCertificate;
import com.radixdlt.consensus.bft.BFTHighQCUpdate;
import com.radixdlt.consensus.epoch.EpochChange;
import com.radixdlt.consensus.liveness.EpochLocalTimeoutOccurrence;
import com.radixdlt.consensus.bft.BFTCommittedUpdate;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.consensus.epoch.EpochView;
import com.radixdlt.environment.EventProcessor;
import com.radixdlt.ledger.LedgerUpdate;
import com.radixdlt.store.LastEpochProof;
import com.radixdlt.store.LastProof;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Manages system information to be consumed by clients such as the api.
 */
public final class InMemorySystemInfo {
	private final AtomicReference<EpochLocalTimeoutOccurrence> lastTimeout = new AtomicReference<>();
	private final AtomicReference<EpochView> currentView = new AtomicReference<>(EpochView.of(0L, View.genesis()));
	private final AtomicReference<QuorumCertificate> highQC = new AtomicReference<>();
	private final AtomicReference<LedgerProof> ledgerProof;
	private final AtomicReference<LedgerProof> epochsLedgerProof;

	@Inject
	public InMemorySystemInfo(
		@LastProof LedgerProof lastProof,
		@LastEpochProof LedgerProof lastEpochProof
	) {
		this.ledgerProof = new AtomicReference<>(lastProof);
		this.epochsLedgerProof = new AtomicReference<>(lastEpochProof);
	}

	public void processTimeout(EpochLocalTimeoutOccurrence timeout) {
		lastTimeout.set(timeout);
	}

	public void processView(EpochView epochView) {
		currentView.set(epochView);
	}

	public EventProcessor<LedgerUpdate> ledgerUpdateEventProcessor() {
		return update -> {
			this.ledgerProof.set(update.getTail());
			var epochChange = (Optional<EpochChange>) update.getStateComputerOutput();
			epochChange.ifPresent(e -> epochsLedgerProof.set(update.getTail()));
		};
	}

	public EventProcessor<BFTHighQCUpdate> bftHighQCEventProcessor() {
		return update -> this.highQC.set(update.getHighQC().highestQC());
	}

	public EventProcessor<BFTCommittedUpdate> bftCommittedUpdateEventProcessor() {
		return update -> {
			this.highQC.set(update.getVertexStoreState().getHighQC().highestQC());
		};
	}

	public LedgerProof getCurrentProof() {
		return ledgerProof.get();
	}

	public LedgerProof getEpochProof() {
		return epochsLedgerProof.get();
	}

	public EpochView getCurrentView() {
		return this.currentView.get();
	}

	public EpochLocalTimeoutOccurrence getLastTimeout() {
		return this.lastTimeout.get();
	}

	public QuorumCertificate getHighestQC() {
		return this.highQC.get();
	}
}
