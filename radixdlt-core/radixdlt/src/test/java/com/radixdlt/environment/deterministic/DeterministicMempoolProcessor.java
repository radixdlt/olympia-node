package com.radixdlt.environment.deterministic;

import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.environment.RemoteEventProcessor;
import com.radixdlt.mempool.MempoolAddSuccess;

import javax.inject.Inject;

public class DeterministicMempoolProcessor implements DeterministicMessageProcessor {
	private RemoteEventProcessor<MempoolAddSuccess> remoteEventProcessor;

	@Inject
	public DeterministicMempoolProcessor(
		RemoteEventProcessor<MempoolAddSuccess> remoteEventProcessor
	) {
		this.remoteEventProcessor = remoteEventProcessor;
	}

	@Override
	public void start() {
	}

	@Override
	public void handleMessage(BFTNode origin, Object o) {
		if (o instanceof MempoolAddSuccess) {
			this.remoteEventProcessor.process(origin, (MempoolAddSuccess) o);
		} else {
			throw new IllegalArgumentException("Unknown message type: " + o.getClass().getName());
		}
	}
}
