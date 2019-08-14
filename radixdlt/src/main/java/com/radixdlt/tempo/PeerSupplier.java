package com.radixdlt.tempo;

import com.radixdlt.common.EUID;
import org.radix.network.peers.Peer;

import java.util.List;
import java.util.Optional;

public interface PeerSupplier {
	/**
	 * Get a list of live and compatible peers
	 * @return A list of peers
	 */
	List<Peer> getPeers();

	/**
	 * Get a list of known node identifiers
	 * @return A list of node identifiers
	 */
	List<EUID> getNids();

	/**
	 * Get the peer associated with a node identifier
	 * @param nid The node identifier
	 * @return The associated peer (if any)
	 */
	Optional<Peer> getPeer(EUID nid);
}
