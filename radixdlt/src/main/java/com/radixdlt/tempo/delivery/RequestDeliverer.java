package com.radixdlt.tempo.delivery;

import com.radixdlt.common.AID;
import org.radix.network.peers.Peer;

import java.util.Collection;

/**
 * A request-based deliverer which can try and deliver given {@link AID}s on request.
 *
 * @see AtomDeliverer
 */
public interface RequestDeliverer {
	/**
	 * Attempt to deliver the atoms associated with the given AIDs.
	 *
	 * This method may not do anything if the atoms have already been delivered,
	 * the peer is unavailable or concurrent requests for the same AIDs are already pending.
	 * @param aids The {@link AID}s to request
	 * @param peer The peer at which the aids are present
	 */
	void tryDeliver(Collection<AID> aids, Peer peer);
}
