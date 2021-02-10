package com.radixdlt.chaos.mempoolfiller;

import com.radixdlt.atommodel.tokens.TransferrableTokensParticle;
import com.radixdlt.engine.StateReducer;
import com.radixdlt.identifiers.RRI;
import com.radixdlt.identifiers.RadixAddress;

import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Supplier;

public final class InMemoryWalletReducer implements StateReducer<InMemoryWallet, TransferrableTokensParticle> {
    private final RRI tokenRRI;
    private final RadixAddress address;

    public InMemoryWalletReducer(RRI tokenRRI, RadixAddress address) {
        this.tokenRRI = Objects.requireNonNull(tokenRRI);
        this.address = Objects.requireNonNull(address);
    }

    @Override
    public Class<InMemoryWallet> stateClass() {
        return InMemoryWallet.class;
    }

    @Override
    public Class<TransferrableTokensParticle> particleClass() {
        return TransferrableTokensParticle.class;
    }

    @Override
    public Supplier<InMemoryWallet> initial() {
        return () -> InMemoryWallet.create(tokenRRI, address);
    }

    @Override
    public BiFunction<InMemoryWallet, TransferrableTokensParticle, InMemoryWallet> outputReducer() {
        return InMemoryWallet::addParticle;
    }

    @Override
    public BiFunction<InMemoryWallet, TransferrableTokensParticle, InMemoryWallet> inputReducer() {
        return InMemoryWallet::removeParticle;
    }
}
