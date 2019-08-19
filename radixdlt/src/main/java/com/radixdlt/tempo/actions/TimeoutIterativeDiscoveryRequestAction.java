package com.radixdlt.tempo.actions;

import com.radixdlt.tempo.LogicalClockCursor;
import com.radixdlt.tempo.TempoAction;
import org.radix.network.peers.Peer;

import java.util.Objects;

public class TimeoutIterativeDiscoveryRequestAction implements TempoAction {
	private final Peer peer;
	private final LogicalClockCursor requestedCursor;

	public TimeoutIterativeDiscoveryRequestAction(Peer peer, LogicalClockCursor requestedCursor) {
		this.peer = Objects.requireNonNull(peer, "peer is required");
		this.requestedCursor = Objects.requireNonNull(requestedCursor, "requestedCursor is required");
	}

	public Peer getPeer() {
		return peer;
	}

	public LogicalClockCursor getRequestedCursor() {
		return requestedCursor;
	}
}
