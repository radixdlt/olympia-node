/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 */

package com.radixdlt.integration.distributed.deterministic;

import com.radixdlt.consensus.CommittedStateSync;
import com.radixdlt.consensus.ConsensusEvent;
import com.radixdlt.consensus.bft.GetVerticesErrorResponse;
import com.radixdlt.consensus.bft.GetVerticesResponse;
import com.radixdlt.consensus.bft.VertexStore.GetVerticesRequest;
import com.radixdlt.consensus.epoch.EpochChange;
import com.radixdlt.consensus.epoch.EpochManager;
import com.radixdlt.consensus.epoch.GetEpochRequest;
import com.radixdlt.consensus.epoch.GetEpochResponse;
import com.radixdlt.consensus.epoch.LocalTimeout;
import com.radixdlt.crypto.Hash;
import java.util.Objects;
import javax.inject.Inject;

/**
 * Subscription Manager (Start/Stop) to the processing of Consensus events under
 * a single BFT Consensus node instance for deterministic tests.
 */
public final class DeterministicConsensusRunner {
	private final EpochManager epochManager;

	@Inject
	public DeterministicConsensusRunner(EpochManager epochManager) {
		this.epochManager = Objects.requireNonNull(epochManager);
	}

	public void start() {
		epochManager.start();
	}

	public void handleMessage(Object message) {
		if (message instanceof ConsensusEvent) {
			this.epochManager.processConsensusEvent((ConsensusEvent) message);
		} else if (message instanceof LocalTimeout) {
			this.epochManager.processLocalTimeout((LocalTimeout) message);
		} else if (message instanceof Hash) {
			this.epochManager.processLocalSync((Hash) message);
		} else if (message instanceof GetVerticesRequest) {
			this.epochManager.processGetVerticesRequest((GetVerticesRequest) message);
		} else if (message instanceof GetVerticesResponse) {
			this.epochManager.processGetVerticesResponse((GetVerticesResponse) message);
		} else if (message instanceof GetVerticesErrorResponse) {
			this.epochManager.processGetVerticesErrorResponse((GetVerticesErrorResponse) message);
		} else if (message instanceof CommittedStateSync) {
			this.epochManager.processCommittedStateSync((CommittedStateSync) message);
		} else if (message instanceof EpochChange) {
			this.epochManager.processEpochChange((EpochChange) message);
		} else if (message instanceof GetEpochRequest) {
			this.epochManager.processGetEpochRequest((GetEpochRequest) message);
		} else if (message instanceof GetEpochResponse) {
			this.epochManager.processGetEpochResponse((GetEpochResponse) message);
		} else {
			throw new IllegalArgumentException("Unknown message type: " + message.getClass().getName());
		}
	}
}
