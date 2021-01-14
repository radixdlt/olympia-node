package com.radixdlt.mempool;

import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.environment.EventProcessor;
import com.radixdlt.environment.RemoteEventDispatcher;
import com.radixdlt.identifiers.EUID;
import com.radixdlt.network.addressbook.AddressBook;
import com.radixdlt.network.addressbook.Peer;
import com.radixdlt.network.addressbook.PeerWithSystem;
import org.radix.universe.system.LocalSystem;

import javax.inject.Inject;
import java.util.Objects;

public final class MempoolRelayer {
	private final PeerWithSystem localPeer;
	private final AddressBook addressBook;
	private final RemoteEventDispatcher<MempoolAddSuccess> remoteEventDispatcher;

	@Inject
	public MempoolRelayer(
		RemoteEventDispatcher<MempoolAddSuccess> remoteEventDispatcher,
		LocalSystem system,
		AddressBook addressBook
	) {
		this.remoteEventDispatcher = Objects.requireNonNull(remoteEventDispatcher);
		this.addressBook = Objects.requireNonNull(addressBook);
		this.localPeer = new PeerWithSystem(system);
	}

	public EventProcessor<MempoolAddSuccess> mempoolAddedCommandEventProcessor() {
		return cmd -> {
			final EUID self = this.localPeer.getNID();
			this.addressBook.peers()
				.filter(Peer::hasSystem) // Only peers with systems (and therefore transports)
				.filter(p -> !self.equals(p.getNID())) // Exclude self, already sent
				.map(p -> BFTNode.create(p.getSystem().getKey()))
				.forEach(peer -> this.remoteEventDispatcher.dispatch(peer, cmd));
		};
	}
}
