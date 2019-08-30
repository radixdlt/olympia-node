package org.radix.network2.addressbook;

import java.util.function.Consumer;

import com.radixdlt.common.EUID;

public interface PeerPersistence {
	boolean savePeer(Peer peer);
	boolean deletePeer(EUID nid);
	void forEachPersistedPeer(Consumer<Peer> c);
}
