package com.radixdlt.keys;

import com.google.inject.AbstractModule;
import com.radixdlt.consensus.HashSigner;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.Self;
import com.radixdlt.crypto.ECKeyPair;

import java.util.Objects;

public final class InMemoryBFTKeyModule extends AbstractModule {
    private final ECKeyPair keyPair;

    public InMemoryBFTKeyModule(ECKeyPair keyPair) {
        this.keyPair = Objects.requireNonNull(keyPair);
    }

    @Override
    protected void configure() {
        bind(HashSigner.class).toInstance(keyPair::sign);
        final BFTNode self = BFTNode.create(keyPair.getPublicKey());
        bind(BFTNode.class).annotatedWith(Self.class).toInstance(self);
    }
}
