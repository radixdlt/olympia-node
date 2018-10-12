package com.radixdlt.client.application.translate;

import com.radixdlt.client.core.address.RadixAddress;
import com.radixdlt.client.core.atoms.particles.AtomFeeConsumable;
import com.radixdlt.client.core.atoms.particles.Consumable;
import com.radixdlt.client.core.ledger.ParticleStore;
import io.reactivex.Observable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class TokenBalanceReducer {
	private final ParticleStore particleStore;
	private final ConcurrentHashMap<RadixAddress, Observable<TokenBalanceState>> cache = new ConcurrentHashMap<>();

	public TokenBalanceReducer(ParticleStore particleStore) {
		this.particleStore = particleStore;
	}

	public Observable<TokenBalanceState> getState(RadixAddress address) {
		return cache.computeIfAbsent(address, addr ->
			particleStore.getParticles(address)
				.filter(p -> p instanceof Consumable && !(p instanceof AtomFeeConsumable))
				.map(p -> (Consumable) p)
				.scanWith(TokenBalanceState::new, TokenBalanceState::merge)
				.debounce(1000, TimeUnit.MILLISECONDS)
				.replay(1)
				.autoConnect()
		);
	}
}
