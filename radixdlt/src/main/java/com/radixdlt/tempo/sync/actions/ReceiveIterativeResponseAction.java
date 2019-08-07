package com.radixdlt.tempo.sync.actions;

import com.google.common.collect.ImmutableList;
import com.radixdlt.common.AID;
import com.radixdlt.tempo.sync.IterativeCursor;
import com.radixdlt.tempo.sync.SyncAction;
import com.radixdlt.tempo.sync.messages.IterativeResponseMessage;
import org.radix.network.peers.Peer;

public class ReceiveIterativeResponseAction implements SyncAction {
	private final ImmutableList<AID> aids;
	private final IterativeCursor cursor;
	private final Peer peer;

	public ReceiveIterativeResponseAction(ImmutableList<AID> aids, IterativeCursor cursor, Peer peer) {
		this.aids = aids;
		this.cursor = cursor;
		this.peer = peer;
	}

	public ImmutableList<AID> getAids() {
		return aids;
	}

	public IterativeCursor getCursor() {
		return cursor;
	}

	public Peer getPeer() {
		return peer;
	}

	public static ReceiveIterativeResponseAction from(IterativeResponseMessage message, Peer peer) {
		return new ReceiveIterativeResponseAction(message.getAids(), message.getCursor(), peer);
	}
}
