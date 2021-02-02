package com.radixdlt.chaos;

import com.google.inject.Inject;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.sync.GetVerticesRequest;
import com.radixdlt.crypto.HashUtils;
import com.radixdlt.environment.EventProcessor;
import com.radixdlt.environment.RemoteEventDispatcher;
import com.radixdlt.environment.ScheduledEventDispatcher;

import java.util.Objects;

public final class BFTNodeMessageFlood {
	private final RemoteEventDispatcher<GetVerticesRequest> requestSender;
	private final ScheduledEventDispatcher<ScheduledMessageFlood> swarmScheduledEventDispatcher;
	private BFTNode nodeToAttack;

	@Inject
	public BFTNodeMessageFlood(
		RemoteEventDispatcher<GetVerticesRequest> requestSender,
        ScheduledEventDispatcher<ScheduledMessageFlood> swarmScheduledEventDispatcher
	) {
		this.requestSender = Objects.requireNonNull(requestSender);
		this.swarmScheduledEventDispatcher = Objects.requireNonNull(swarmScheduledEventDispatcher);
	}

	public EventProcessor<MessageFloodUpdate> messageFloodUpdateProcessor() {
		return msg -> {
			BFTNode nextNode = msg.getBFTNode().orElse(null);
		    if (Objects.equals(this.nodeToAttack, nextNode)) {
		    	return;
			}

		    if (this.nodeToAttack == null) {
		    	swarmScheduledEventDispatcher.dispatch(ScheduledMessageFlood.create(), 50);
			}

			this.nodeToAttack = nextNode;
		};
	}

	public EventProcessor<ScheduledMessageFlood> scheduledMessageFloodProcessor() {
		return s -> {
			if (nodeToAttack == null) {
				return;
			}

			GetVerticesRequest request = new GetVerticesRequest(HashUtils.random256(), 1);
			for (int i = 0; i < 100; i++) {
				requestSender.dispatch(nodeToAttack, request);
			}

			swarmScheduledEventDispatcher.dispatch(ScheduledMessageFlood.create(), 50);
		};
	}
}
