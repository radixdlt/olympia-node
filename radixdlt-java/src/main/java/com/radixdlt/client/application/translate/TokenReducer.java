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
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class TokenReducer {
	private final ConcurrentHashMap<RadixAddress, Observable<Map<TokenRef, TokenState>>> cache = new ConcurrentHashMap<>();
	private final ParticleStore particleStore;

	public TokenReducer(ParticleStore particleStore) {
		this.particleStore = particleStore;
	}

	public Observable<Map<TokenRef, TokenState>> getState(RadixAddress address) {
		return cache.computeIfAbsent(address, addr -> {
			Observable<Map<String, TokenParticle>> tokenParticles = particleStore.getParticles(address)
				.filter(p -> p instanceof TokenParticle)
				.map(p -> (TokenParticle) p)
				.scanWith(HashMap<String, TokenParticle>::new, (map, p) -> {
					HashMap<String, TokenParticle> newMap = new HashMap<>(map);
					newMap.put(p.getTokenRef().getIso(), p);
					return newMap;
				});

			Observable<HashMap<String, Long>> mintedTokens = particleStore.getParticles(address)
				.filter(p -> p instanceof Minted)
				.map(p -> (Minted) p)
				.scanWith(HashMap<String, Long>::new, (map, p) -> {
					HashMap<String, Long> newMap = new HashMap<>(map);
					newMap.merge(p.getTokenRef().getIso(), p.getAmount(), Long::sum);
					return newMap;
				});

			return Observable.combineLatest(tokenParticles, mintedTokens, (tokens, minted) ->
				tokens.entrySet().stream().collect(Collectors.toMap(
					e -> e.getValue().getTokenRef(),
					e -> {
						TokenParticle p = e.getValue();
						Long subUnits = Optional.ofNullable(minted.get(p.getTokenRef().getIso())).orElse(0L);
						BigDecimal totalSupply = TokenRef.subUnitsToDecimal(subUnits);
						return new TokenState(p.getName(), p.getIso(), p.getDescription(), totalSupply);
					}
				))
			).debounce(1000, TimeUnit.MILLISECONDS);
		});
	}

}
