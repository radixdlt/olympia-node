package com.radixdlt.mempool;

public final class P2PNeighborUpdate {
    private final P2PNeighbors p2pNeighbors;

    private P2PNeighborUpdate(P2PNeighbors p2pNeighbors) {
        this.p2pNeighbors = p2pNeighbors;
    }

    public P2PNeighbors getP2PNeighbors() {
        return p2pNeighbors;
    }

    public static P2PNeighborUpdate create(P2PNeighbors p2pNeighbors) {
        return new P2PNeighborUpdate(p2pNeighbors);
    }
}
