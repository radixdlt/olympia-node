package com.radixdlt.client.core.ledger.selector;

import com.radixdlt.client.core.network.RadixPeerState;

import java.util.function.Predicate;

/**
 * Peer filter that test the desirability of a peer
 */
public interface RadixPeerFilter extends Predicate<RadixPeerState> {
}
