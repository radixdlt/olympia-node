package com.radixdlt.client.application.translate;

import com.radixdlt.client.application.objects.Amount;
import com.radixdlt.client.application.objects.Token;
import com.radixdlt.client.core.address.RadixAddress;
import com.radixdlt.client.core.atoms.AtomFeeConsumable;
import com.radixdlt.client.core.atoms.Consumable;
import com.radixdlt.client.core.atoms.RadixHash;
import com.radixdlt.client.core.atoms.Spin;
import com.radixdlt.client.core.ledger.ParticleStore;
import io.reactivex.Observable;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class AddressTokenReducer {

	private final Observable<AddressTokenState> state;

	public AddressTokenReducer(RadixAddress address, ParticleStore particleStore) {
		this.state = particleStore.getConsumables(address)
			.filter(p -> !(p instanceof AtomFeeConsumable))
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
				long balanceInSubUnits = consumables.stream().mapToLong(Consumable::getAmount).sum();
				Amount balance = Amount.subUnitsOf(balanceInSubUnits, Token.TEST);
				return new AddressTokenState(balance, consumables);
			})
			.replay(1)
			.autoConnect();
	}

	public Observable<AddressTokenState> getState() {
		return state;
	}
}
