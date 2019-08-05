package com.radixdlt.tempo.sync.actions;

import com.radixdlt.tempo.TempoAtom;
import com.radixdlt.tempo.sync.SyncAction;
import com.radixdlt.tempo.sync.messages.PushMessage;
import org.radix.network.peers.Peer;

public class SendPushAction implements SyncAction {
	private final TempoAtom atom;
	private final Peer peer;

	public SendPushAction(TempoAtom atom, Peer peer) {
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

	public static SendPushAction from(PushMessage message, Peer peer) {
		return new SendPushAction(message.getAtom(), peer);
	}
}
