package com.radixdlt.tempo.sync.actions;

import com.radixdlt.tempo.TempoAtom;
import com.radixdlt.tempo.sync.SyncAction;
import com.radixdlt.tempo.sync.messages.PushMessage;
import org.radix.network.peers.Peer;

public class ReceivePushAction implements SyncAction {
	private final TempoAtom atom;
	private final Peer peer;

	public ReceivePushAction(TempoAtom atom, Peer peer) {
		this.atom = atom;
		this.peer = peer;
	}

	public TempoAtom getAtom() {
		return atom;
	}

	public Peer getPeer() {
		return peer;
	}

	public PushMessage toMessage() {
		return new PushMessage(atom);
	}

	public static ReceivePushAction from(PushMessage message, Peer peer) {
		return new ReceivePushAction(message.getAtom(), peer);
	}
}
