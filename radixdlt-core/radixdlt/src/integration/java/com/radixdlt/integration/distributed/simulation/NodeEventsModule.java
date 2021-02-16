package com.radixdlt.integration.distributed.simulation;

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
import com.radixdlt.environment.ProcessOnDispatch;
import com.radixdlt.integration.distributed.simulation.invariants.consensus.NodeEvents;

public final class NodeEventsModule extends AbstractModule {
	// TODO: Cleanup and separate epoch timeouts and non-epoch timeouts
	@ProcessOnDispatch
	@ProvidesIntoSet
	private EventProcessor<EpochLocalTimeoutOccurrence> epochTimeoutProcessor(
		@Self BFTNode node,
		NodeEvents nodeEvents
	) {
		return nodeEvents.processor(node, EpochLocalTimeoutOccurrence.class);
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
}
