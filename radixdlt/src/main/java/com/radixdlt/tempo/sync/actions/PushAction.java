package com.radixdlt.tempo.sync.actions;

import com.radixdlt.tempo.sync.SyncAction;
import com.radixdlt.tempo.sync.messages.PushMessage;
import org.radix.atoms.Atom;
import org.radix.network.peers.Peer;

public class PushAction implements SyncAction {
	private final Atom atom;
	private final Peer peer;

	public PushAction(Atom atom, Peer peer) {
		this.atom = atom;
		this.peer = peer;
	}

	public Atom getAtom() {
		return atom;
	}

	public Peer getPeer() {
		return peer;
	}

	public PushMessage toMessage() {
		return new PushMessage(atom);
	}

	public static PushAction from(PushMessage message, Peer peer) {
		return new PushAction(message.getAtom(), peer);
	}
}
