package com.radixdlt.client.application.translate;

import com.radixdlt.client.core.address.RadixAddress;
import com.radixdlt.client.core.atoms.TokenRef;
import com.radixdlt.client.core.atoms.particles.Minted;
import com.radixdlt.client.core.atoms.particles.TokenParticle;
import com.radixdlt.client.core.ledger.ParticleStore;
import io.reactivex.Observable;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class TokenReducer {
	private final ConcurrentHashMap<RadixAddress, Observable<Map<TokenRef, TokenState>>> cache = new ConcurrentHashMap<>();
	private final ParticleStore particleStore;

	public TokenReducer(ParticleStore particleStore) {
		this.particleStore = particleStore;
	}

	public Observable<Map<TokenRef, TokenState>> getState(RadixAddress address) {
		return cache.computeIfAbsent(address, addr ->
			particleStore.getParticles(address)
				.filter(p -> p instanceof TokenParticle || p instanceof Minted)
				.scanWith(HashMap<TokenRef, TokenState>::new, (map, p) -> {
					HashMap<TokenRef, TokenState> newMap = new HashMap<>(map);
					if (p instanceof TokenParticle) {
						TokenParticle tokenParticle = (TokenParticle) p;
						TokenState tokenState = new TokenState(
							tokenParticle.getName(),
							tokenParticle.getIso(),
							tokenParticle.getDescription(),
							BigDecimal.ZERO
						);

						newMap.merge(
							tokenParticle.getTokenRef(),
							tokenState,
							(a, b) -> new TokenState(b.getName(), b.getIso(), b.getDescription(), a.getTotalSupply())
						);
					} else if (p instanceof Minted) {
						Minted minted = (Minted) p;
						TokenState tokenState = new TokenState(
							null,
							minted.getTokenRef().getIso(),
							null,
							BigDecimal.ZERO
						);
						newMap.merge(
							minted.getTokenRef(),
							tokenState,
							(a, b) -> new TokenState(
								a.getName(),
								a.getIso(),
								a.getDescription(),
								a.getTotalSupply().add(b.getTotalSupply())
							)
						);
					}

					return newMap;
				})
				.map(m -> (Map<TokenRef, TokenState>) m)
				.debounce(1000, TimeUnit.MILLISECONDS)
		);
	}
}
