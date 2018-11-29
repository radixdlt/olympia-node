package com.radixdlt.client.core.ledger.selector;

import com.radixdlt.client.core.network.RadixPeer;
import com.radixdlt.client.core.network.WebSocketClient;

import java.util.function.BiPredicate;

/**
 * Peer filter that test the desirability of a peer
 */
public interface RadixPeerFilter extends BiPredicate<RadixPeer, WebSocketClient.RadixClientStatus> {
}
