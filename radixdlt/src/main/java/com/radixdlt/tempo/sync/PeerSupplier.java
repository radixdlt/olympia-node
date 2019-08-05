package com.radixdlt.tempo.sync;

import com.radixdlt.common.EUID;
import org.radix.network.peers.Peer;

import java.util.List;

public interface PeerSupplier {
	List<Peer> getPeers();

	List<EUID> getNids();
}
