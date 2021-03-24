package com.radixdlt.chaos.mempoolfiller;

import com.radixdlt.atommodel.tokens.TransferrableTokensParticle;
import com.radixdlt.engine.StateReducer;
import com.radixdlt.identifiers.RRI;
import com.radixdlt.identifiers.RadixAddress;

import java.util.Objects;
import java.util.Random;
import java.util.function.BiFunction;
import java.util.function.Supplier;

/**
 * TODO: Remove as reducer doesn't work with radix engine transient branches due to mutability of
 * TODO: in memory wallet. Should refactor to better radix engine application framework which doesn't
 * TODO: require state to all be in memory.
 */
public final class InMemoryWalletReducer implements StateReducer<InMemoryWallet, TransferrableTokensParticle> {
	private final RRI tokenRRI;
	private final RadixAddress address;
	private final Random random;

	public InMemoryWalletReducer(
		RRI tokenRRI,
		RadixAddress address,
		Random random
	) {
		this.tokenRRI = Objects.requireNonNull(tokenRRI);
		this.address = Objects.requireNonNull(address);
		this.random = Objects.requireNonNull(random);
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
		return () -> InMemoryWallet.create(tokenRRI, address, random);
	}

	@Override
	public BiFunction<InMemoryWallet, TransferrableTokensParticle, InMemoryWallet> outputReducer() {
		return (wallet, p) -> {
			wallet.addParticle(p);
			return wallet;
		};
	}

	@Override
	public BiFunction<InMemoryWallet, TransferrableTokensParticle, InMemoryWallet> inputReducer() {
		return (wallet, p) -> {
			wallet.removeParticle(p);
			return wallet;
		};
	}
}
