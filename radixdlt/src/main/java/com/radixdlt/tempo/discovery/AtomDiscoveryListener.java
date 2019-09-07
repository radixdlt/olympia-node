package com.radixdlt.tempo.discovery;

import com.radixdlt.common.AID;
import org.radix.network2.addressbook.Peer;

import java.util.Collection;
import java.util.Set;
import java.util.function.BiConsumer;

/**
 * Thread-safe sink for discoveries of a certain set of aids at a given peer.
 */
public interface AtomDiscoveryListener extends BiConsumer<Set<AID>, Peer> {
	// only extends consumer interface
}
