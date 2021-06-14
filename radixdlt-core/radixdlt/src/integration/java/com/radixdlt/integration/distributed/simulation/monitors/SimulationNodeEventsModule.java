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
 *
 */

package com.radixdlt.integration.distributed.simulation.monitors;

import com.google.inject.AbstractModule;
import com.google.inject.multibindings.ProvidesIntoSet;
import com.radixdlt.consensus.bft.BFTCommittedUpdate;
import com.radixdlt.consensus.bft.BFTHighQCUpdate;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.Self;
import com.radixdlt.consensus.liveness.EpochLocalTimeoutOccurrence;
import com.radixdlt.consensus.liveness.LocalTimeoutOccurrence;
import com.radixdlt.consensus.sync.GetVerticesRequest;
import com.radixdlt.environment.EventProcessor;
import com.radixdlt.environment.EventProcessorOnDispatch;
import com.radixdlt.environment.ProcessOnDispatch;
import com.radixdlt.statecomputer.InvalidProposedTxn;

/**
 * Module which manages node testing events for simulation
 */
public final class SimulationNodeEventsModule extends AbstractModule {
	@ProvidesIntoSet
	private EventProcessorOnDispatch<?> epochTimeoutProcessor(
		@Self BFTNode node,
		NodeEvents nodeEvents
	) {
		return nodeEvents.processorOnDispatch(node, EpochLocalTimeoutOccurrence.class);
	}

	@ProcessOnDispatch
	@ProvidesIntoSet
	private EventProcessor<LocalTimeoutOccurrence> timeoutEventProcessor(
		@Self BFTNode node,
		NodeEvents nodeEvents
	) {
		return nodeEvents.processor(node, LocalTimeoutOccurrence.class);
	}

	@ProvidesIntoSet
	@ProcessOnDispatch
	private EventProcessor<GetVerticesRequest> requestProcessor(
		@Self BFTNode node,
		NodeEvents nodeEvents
	) {
		return nodeEvents.processor(node, GetVerticesRequest.class);
	}

	@ProvidesIntoSet
	@ProcessOnDispatch
	private EventProcessor<BFTCommittedUpdate> committedProcessor(
		@Self BFTNode node,
		NodeEvents nodeEvents
	) {
		return nodeEvents.processor(node, BFTCommittedUpdate.class);
	}

	@ProvidesIntoSet
	@ProcessOnDispatch
	private EventProcessor<BFTHighQCUpdate> highQCProcessor(
		@Self BFTNode node,
		NodeEvents nodeEvents
	) {
		return nodeEvents.processor(node, BFTHighQCUpdate.class);
	}

	@ProvidesIntoSet
	private EventProcessorOnDispatch<?> invalidCommandsProcessor(
		@Self BFTNode node,
		NodeEvents nodeEvents
	) {
		return nodeEvents.processorOnDispatch(node, InvalidProposedTxn.class);
	}
}
