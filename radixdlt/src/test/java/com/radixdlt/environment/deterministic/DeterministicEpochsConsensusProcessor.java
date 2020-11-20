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

package com.radixdlt.environment.deterministic;

import com.radixdlt.consensus.ConsensusEvent;
import com.radixdlt.consensus.bft.BFTUpdate;
import com.radixdlt.consensus.sync.GetVerticesErrorResponse;
import com.radixdlt.consensus.sync.GetVerticesResponse;
import com.radixdlt.consensus.epoch.EpochManager;
import com.radixdlt.consensus.epoch.GetEpochRequest;
import com.radixdlt.consensus.epoch.GetEpochResponse;
import com.radixdlt.consensus.epoch.LocalTimeout;
import com.radixdlt.consensus.sync.GetVerticesRequest;
import com.radixdlt.epochs.EpochsLedgerUpdate;
import java.util.Objects;
import javax.annotation.concurrent.NotThreadSafe;
import javax.inject.Inject;

/**
 * Processor of consensus events which gets executed one at a time
 */
@NotThreadSafe
public final class DeterministicEpochsConsensusProcessor implements DeterministicMessageProcessor {
	private final EpochManager epochManager;

	@Inject
	public DeterministicEpochsConsensusProcessor(EpochManager epochManager) {
		this.epochManager = Objects.requireNonNull(epochManager);
	}

	@Override
	public void start() {
		epochManager.start();
	}

	@Override
	public void handleMessage(Object message) {
		if (message instanceof ConsensusEvent) {
			this.epochManager.processConsensusEvent((ConsensusEvent) message);
		} else if (message instanceof LocalTimeout) {
			this.epochManager.processLocalTimeout((LocalTimeout) message);
		} else if (message instanceof BFTUpdate) {
			this.epochManager.processBFTUpdate((BFTUpdate) message);
		} else if (message instanceof GetVerticesRequest) {
			this.epochManager.processGetVerticesRequest((GetVerticesRequest) message);
		} else if (message instanceof GetVerticesResponse) {
			this.epochManager.processGetVerticesResponse((GetVerticesResponse) message);
		} else if (message instanceof GetVerticesErrorResponse) {
			this.epochManager.processGetVerticesErrorResponse((GetVerticesErrorResponse) message);
		} else if (message instanceof GetEpochRequest) {
			this.epochManager.processGetEpochRequest((GetEpochRequest) message);
		} else if (message instanceof GetEpochResponse) {
			this.epochManager.processGetEpochResponse((GetEpochResponse) message);
		} else if (message instanceof EpochsLedgerUpdate) {
			this.epochManager.processLedgerUpdate((EpochsLedgerUpdate) message);
		} else {
			throw new IllegalArgumentException("Unknown message type: " + message.getClass().getName());
		}
	}
}
