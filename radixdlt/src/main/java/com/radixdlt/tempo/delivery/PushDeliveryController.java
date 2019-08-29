package com.radixdlt.tempo.delivery;

import com.radixdlt.tempo.PeerSupplier;
import com.radixdlt.tempo.TempoAtom;
import com.radixdlt.tempo.messages.PushMessage;
import org.radix.network.peers.Peer;
import org.radix.network2.messaging.MessageCentral;
import org.radix.time.TemporalVertex;

import java.io.Closeable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Objects;
import java.util.Optional;

public final class PushDeliveryController implements Closeable {
	private final MessageCentral messageCentral;
	private final PeerSupplier peerSupplier;

	private final Collection<AtomDeliveryListener> deliveryListeners;

	public PushDeliveryController(
			MessageCentral messageCentral,
	        PeerSupplier peerSupplier
	) {
		this.messageCentral = Objects.requireNonNull(messageCentral);
		this.peerSupplier = Objects.requireNonNull(peerSupplier);

		// TODO improve locking to something like in messaging
		this.deliveryListeners = Collections.synchronizedList(new ArrayList<>());

		messageCentral.addListener(PushMessage.class, this::onReceivePush);
	}

	public void accept(TempoAtom atom, TemporalVertex ownVertex) {
		PushMessage push = new PushMessage(atom);
		ownVertex.getEdges().stream()
			.map(peerSupplier::getPeer)
			.filter(Optional::isPresent)
			.map(Optional::get)
			.forEach(peer -> messageCentral.send(peer, push));
	}

	private void onReceivePush(Peer peer, PushMessage message) {
		notifyListeners(peer, message.getAtom());
	}

	private void notifyListeners(Peer peer, TempoAtom atom) {
		deliveryListeners.forEach(listener -> listener.accept(atom, peer));
	}

	public void addListener(AtomDeliveryListener deliveryListener) {
		deliveryListeners.add(deliveryListener);
	}

	public void removeListener(AtomDeliveryListener deliveryListener) {
		deliveryListeners.remove(deliveryListener);
	}

	@Override
	public void close() {
		messageCentral.removeListener(PushMessage.class, this::onReceivePush);
	}
}
