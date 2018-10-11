package com.radixdlt.client.application.translate;

import com.radixdlt.client.core.address.RadixAddress;
import com.radixdlt.client.core.atoms.Token;
import com.radixdlt.client.core.atoms.particles.AtomFeeConsumable;
import com.radixdlt.client.core.atoms.particles.Consumable;
import com.radixdlt.client.core.atoms.RadixHash;
import com.radixdlt.client.core.atoms.particles.Spin;
import com.radixdlt.client.core.ledger.ParticleStore;
import io.reactivex.Observable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class AddressTokenReducer {

	private final Observable<AddressTokenState> state;

	public AddressTokenReducer(RadixAddress address, ParticleStore particleStore) {
		this.state = particleStore.getParticles(address)
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
				Map<Token, Long> balance = consumables.stream().collect(
					Collectors.groupingBy(
						Consumable::getTokenReference, Collectors.summingLong(Consumable::getAmount)
					)
				);

				Map<Token, List<Consumable>> consumableLists = consumables.stream().collect(
					Collectors.groupingBy(Consumable::getTokenReference)
				);

				return new AddressTokenState(balance, consumableLists);
			})
			.replay(1)
			.autoConnect();
	}

	public Observable<AddressTokenState> getState() {
		return state;
	}
}
