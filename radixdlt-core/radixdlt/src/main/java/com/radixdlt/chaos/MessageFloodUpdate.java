package com.radixdlt.chaos;

import com.radixdlt.consensus.bft.BFTNode;

import java.util.Objects;
import java.util.Optional;

public final class MessageFloodUpdate {
    private BFTNode bftNode;

    private MessageFloodUpdate(BFTNode bftNode) {
        this.bftNode = bftNode;
    }

    public static MessageFloodUpdate create(BFTNode bftNode) {
        Objects.requireNonNull(bftNode);
        return new MessageFloodUpdate(bftNode);
    }

    public static MessageFloodUpdate disable() {
        return new MessageFloodUpdate(null);
    }

    public Optional<BFTNode> getBFTNode() {
        return Optional.ofNullable(bftNode);
    }
}
