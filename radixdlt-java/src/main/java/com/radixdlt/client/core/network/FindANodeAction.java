package com.radixdlt.client.core.network;

import java.util.Set;

public interface FindANodeAction {
	Set<Long> shards();
	RadixNodeAction foundNode(RadixPeer node);
}
