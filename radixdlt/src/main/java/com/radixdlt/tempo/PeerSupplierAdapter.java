package com.radixdlt.tempo;

import com.radixdlt.common.EUID;

import org.radix.network2.addressbook.AddressBook;
import org.radix.network2.addressbook.Peer;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class PeerSupplierAdapter implements PeerSupplier {
	private final Supplier<AddressBook> addressbookSupplier;

	public PeerSupplierAdapter(Supplier<AddressBook> addressbookSupplier) {
		this.addressbookSupplier = addressbookSupplier;
	}

	@Override
	public List<Peer> getPeers() {
		return addressbookSupplier.get().recentPeers().collect(Collectors.toList());
	}

	@Override
	public List<EUID> getNids() {
		return addressbookSupplier.get().recentPeers()
			.filter(Peer::hasNID)
			.map(peer -> peer.getSystem().getNID())
			.collect(Collectors.toList());
	}

	@Override
	public Optional<Peer> getPeer(EUID nid) {
<<<<<<< HEAD
		try {
			return Optional.ofNullable(peerHandlerSupplier.get().getPeer(PeerHandler.PeerDomain.NETWORK, nid));
		} catch (DatabaseException e) {
			throw new TempoException("Error while getting peers", e);
		}
=======
		return Optional.of(addressbookSupplier.get().peer(nid));
>>>>>>> e676c83... First draft of addressbook with unit and integration tests running
	}
}
