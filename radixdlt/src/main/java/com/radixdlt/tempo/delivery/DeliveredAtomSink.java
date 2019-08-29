package com.radixdlt.tempo.delivery;

import com.radixdlt.common.AID;
import org.radix.network.peers.Peer;

import java.util.Collection;
import java.util.function.BiConsumer;

/**
 * Thread-safe requestor for deliveries of a certain set of aids from a given peer.
 */
public interface DeliveredAtomSink extends BiConsumer<Collection<AID>, Peer> {
	// only extends consumer interface
}
