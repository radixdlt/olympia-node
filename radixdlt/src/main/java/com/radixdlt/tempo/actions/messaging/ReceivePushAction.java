package com.radixdlt.tempo.actions.messaging;

import com.radixdlt.tempo.TempoAtom;
import com.radixdlt.tempo.reactive.TempoAction;
import com.radixdlt.tempo.messages.PushMessage;
import org.radix.network.peers.Peer;

import java.util.Objects;

public class ReceivePushAction implements TempoAction {
	private final TempoAtom atom;
	private final Peer peer;

	public ReceivePushAction(TempoAtom atom, Peer peer) {
		this.atom = Objects.requireNonNull(atom, "atom is required");
		this.peer = Objects.requireNonNull(peer, "peer is required");
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
