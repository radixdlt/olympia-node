package com.radixdlt.client.core.network.selector;

import com.radixdlt.client.core.network.RadixNodeState;

import java.util.function.Predicate;

/**
 * Peer filter that test the desirability of a peer
 */
public interface RadixPeerFilter extends Predicate<RadixNodeState> {
}
