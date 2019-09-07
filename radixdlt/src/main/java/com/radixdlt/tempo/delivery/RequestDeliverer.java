package com.radixdlt.tempo.delivery;

import com.google.common.collect.ImmutableSet;
import com.radixdlt.common.AID;
import org.radix.network2.addressbook.Peer;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * A request-based deliverer which can try and deliver given {@link AID}s on request.
 *
 * @see AtomDeliverer
 */
public interface RequestDeliverer {
	/**
	 * Attempt to deliver the atoms associated with the given AIDs.
	 *
	 * This method may not have any effect if the atoms have already been delivered,
	 * the peer is unavailable or concurrent requests for the same AIDs are already pending.
	 * @param peer The peer at which the aids are present
	 * @return a future containing the result of this request
	 */
	default CompletableFuture<DeliveryResult> deliver(AID aid, Peer peer) {
		return deliver(ImmutableSet.of(aid), ImmutableSet.of(peer)).get(aid);
	}

	/**
	 * Attempt to deliver the atoms associated with the given AIDs.
	 *
	 * This method may not have any effect if the atoms have already been delivered,
	 * the peer is unavailable or concurrent requests for the same AIDs are already pending.
	 * @param aids The {@link AID}s to request
	 * @param peers The peers at which the aids are present
	 * @return a future containing the results of this request
	 */
	Map<AID, CompletableFuture<DeliveryResult>> deliver(Set<AID> aids, Set<Peer> peers);
}
