package com.radixdlt.client.application.translate;

import com.radixdlt.client.core.address.RadixAddress;
import com.radixdlt.client.core.atoms.TokenRef;
import com.radixdlt.client.core.atoms.particles.AtomFeeConsumable;
import com.radixdlt.client.core.atoms.particles.Consumable;
import com.radixdlt.client.core.atoms.RadixHash;
import com.radixdlt.client.core.atoms.particles.Spin;
import com.radixdlt.client.core.ledger.ParticleStore;
import io.reactivex.Observable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

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
				.scanWith(HashMap<RadixHash, Consumable>::new, (map, p) -> {
					HashMap<RadixHash, Consumable> newMap = new HashMap<>(map);
					newMap.put(p.getHash(), p);
					return newMap;
				})
				.map(map -> map.values().stream()
					.filter(c -> c.getSpin() == Spin.UP)
					.collect(Collectors.toList())
				)
				.debounce(1000, TimeUnit.MILLISECONDS)
				.map(consumables -> {
					Map<TokenRef, Long> balance = consumables.stream().collect(
						Collectors.groupingBy(
							Consumable::getTokenRef, Collectors.summingLong(Consumable::getAmount)
						)
					);

					Map<TokenRef, List<Consumable>> consumableLists = consumables.stream().collect(
						Collectors.groupingBy(Consumable::getTokenRef)
					);

					return new TokenBalanceState(balance, consumableLists);
				})
				.replay(1)
				.autoConnect()
		);
	}
}
