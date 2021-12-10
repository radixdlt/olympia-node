package com.radixdlt.store.tree.serialization;

import com.radixdlt.store.tree.PMTNode;

public interface PMTNodeSerializer {
    byte[] serialize(PMTNode node);
    PMTNode deserialize(byte[] serializedNode);
}
