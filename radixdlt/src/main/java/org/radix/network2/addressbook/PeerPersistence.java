package org.radix.network2.addressbook;

import java.io.Closeable;
import java.util.function.Consumer;

import com.radixdlt.common.EUID;

/**
 * Persistence interface for persisting peers in AddressBook.
 */
public interface PeerPersistence extends Closeable  {

	/**
	 * Saves peer to database.
	 *
	 * @param peer the peer to save.  The peer must have a valid NID.
	 * @return {@code true} if the peer was saved, {@code false} otherwise
	 */
	boolean savePeer(Peer peer);

	/**
	 * Deletes peer identified by specified NID.
	 *
	 * @param NID of the peer to delete
	 * @return {@code true} if the peer was deleted, {@code false} otherwise
	 */
	boolean deletePeer(EUID nid);

	/**
	 * Calls the specified consumer with each persisted peer.
	 *
	 * @param c the consumer to call with each persisted peer
	 */
	void forEachPersistedPeer(Consumer<Peer> c);
}
