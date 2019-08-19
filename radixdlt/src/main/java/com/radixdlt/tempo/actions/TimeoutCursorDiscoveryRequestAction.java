package com.radixdlt.tempo.actions;

import com.radixdlt.tempo.LogicalClockCursor;
import com.radixdlt.tempo.TempoAction;
import org.radix.network.peers.Peer;

import java.util.Objects;

public class TimeoutCursorDiscoveryRequestAction implements TempoAction {
	private final LogicalClockCursor requestedCursor;
	private final Peer peer;

	public TimeoutCursorDiscoveryRequestAction(LogicalClockCursor requestedCursor, Peer peer) {
		this.peer = Objects.requireNonNull(peer, "peer is required");
		this.requestedCursor = Objects.requireNonNull(requestedCursor, "requestedCursor is required");
	}

	public LogicalClockCursor getRequestedCursor() {
		return requestedCursor;
	}

	public Peer getPeer() {
		return peer;
	}
}
