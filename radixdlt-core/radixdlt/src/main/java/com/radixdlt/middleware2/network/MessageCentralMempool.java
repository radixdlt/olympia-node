package com.radixdlt.middleware2.network;

import com.radixdlt.consensus.Command;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.environment.RemoteEventDispatcher;
import com.radixdlt.environment.rx.RemoteEvent;
import com.radixdlt.mempool.MempoolAddSuccess;
import com.radixdlt.mempool.messages.MempoolAtomAddedMessage;
import com.radixdlt.network.addressbook.AddressBook;
import com.radixdlt.network.addressbook.PeerWithSystem;
import com.radixdlt.network.messaging.MessageCentral;
import com.radixdlt.network.messaging.MessageListener;
import com.radixdlt.universe.Universe;
import io.reactivex.rxjava3.core.Observable;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.radix.network.messaging.Message;

import javax.inject.Inject;
import java.util.Objects;
import java.util.Optional;

public class MessageCentralMempool {
	private static final Logger log = LogManager.getLogger();

	private final MessageCentral messageCentral;
	private final int magic;
	private final AddressBook addressBook;

	@Inject
	public MessageCentralMempool(
		Universe universe,
		MessageCentral messageCentral,
		AddressBook addressBook
	) {
		this.magic = universe.getMagic();
		this.messageCentral = Objects.requireNonNull(messageCentral);
		this.addressBook = addressBook;
	}

	public RemoteEventDispatcher<MempoolAddSuccess> commandRemoteEventDispatcher() {
		return (receiver, msg) -> {
			MempoolAtomAddedMessage message = new MempoolAtomAddedMessage(this.magic, msg.getCommand());
			this.send(message, receiver);
		};
	}

	private boolean send(Message message, BFTNode recipient) {
		Optional<PeerWithSystem> peer = this.addressBook.peer(recipient.getKey().euid());

		if (!peer.isPresent()) {
			log.error("Peer {} not present", recipient);
			return false;
		} else {
			this.messageCentral.send(peer.get(), message);
			return true;
		}
	}

	public Observable<RemoteEvent<Command>> mempoolComands() {
		return Observable.create(emitter -> {
			MessageListener<MempoolAtomAddedMessage> listener = (src, msg) -> {

				BFTNode node = BFTNode.create(src.getSystem().getKey());
				emitter.onNext(RemoteEvent.create(node, msg.command(), Command.class));
			};
			this.messageCentral.addListener(MempoolAtomAddedMessage.class, listener);
			emitter.setCancellable(() -> this.messageCentral.removeListener(listener));
		});
	}
}
