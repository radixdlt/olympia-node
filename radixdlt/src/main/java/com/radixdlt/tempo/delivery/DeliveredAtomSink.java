package com.radixdlt.tempo.delivery;

import com.radixdlt.tempo.TempoAtom;
import org.radix.network.peers.Peer;

import java.util.function.BiConsumer;

/**
 * Thread-safe sink for deliveries of a certain atom from a given peer.
 */
public interface DeliveredAtomSink extends BiConsumer<TempoAtom, Peer> {
	// only extends consumer interface
}
