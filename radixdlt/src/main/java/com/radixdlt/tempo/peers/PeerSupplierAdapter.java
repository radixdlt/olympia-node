package com.radixdlt.tempo.peers;

import com.radixdlt.common.EUID;
import com.radixdlt.tempo.exceptions.TempoException;
import org.radix.database.exceptions.DatabaseException;
import org.radix.network.peers.Peer;
import org.radix.network.peers.PeerHandler;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class PeerSupplierAdapter implements PeerSupplier {
	private final Supplier<PeerHandler> peerHandlerSupplier;

	public PeerSupplierAdapter(Supplier<PeerHandler> peerHandlerSupplier) {
		this.peerHandlerSupplier = peerHandlerSupplier;
	}

	@Override
	public List<Peer> getPeers() {
		try {
			return peerHandlerSupplier.get().getPeers(PeerHandler.PeerDomain.NETWORK);
		} catch (DatabaseException e) {
			throw new TempoException("Error while getting peers", e);
		}
	}

	@Override
	public List<EUID> getNids() {
		try {
			return peerHandlerSupplier.get().getPeers(PeerHandler.PeerDomain.NETWORK).stream()
				.map(peer -> peer.getSystem().getNID())
				.collect(Collectors.toList());
		} catch (DatabaseException e) {
			throw new TempoException("Error while getting peers", e);
		}
	}

	@Override
	public Optional<Peer> getPeer(EUID nid) {
		try {
			return Optional.of(peerHandlerSupplier.get().getPeer(PeerHandler.PeerDomain.NETWORK, nid));
		} catch (DatabaseException e) {
			throw new TempoException("Error while getting peers", e);
		}
	}
}
