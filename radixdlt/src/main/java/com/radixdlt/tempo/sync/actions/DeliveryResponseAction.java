package com.radixdlt.tempo.sync.actions;

import com.radixdlt.tempo.sync.SyncAction;
import com.radixdlt.tempo.sync.messages.DeliveryResponseMessage;
import org.radix.atoms.Atom;
import org.radix.network.peers.Peer;

public class DeliveryResponseAction implements SyncAction {
	private final Atom atom;
	private final Peer peer;

	public DeliveryResponseAction(Atom atom, Peer peer) {
		this.atom = atom;
		this.peer = peer;
	}

	public Atom getAtom() {
		return atom;
	}

	public Peer getPeer() {
		return peer;
	}

	public DeliveryResponseMessage toMessage() {
		return new DeliveryResponseMessage(atom);
	}

	public static DeliveryResponseAction from(DeliveryResponseMessage message, Peer peer) {
		return new DeliveryResponseAction(message.getAtom(), peer);
	}
}
