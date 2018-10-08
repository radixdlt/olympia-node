package com.radixdlt.client.application.translate;

import com.radixdlt.client.assets.Amount;
import com.radixdlt.client.assets.Asset;
import com.radixdlt.client.core.address.RadixAddress;
import com.radixdlt.client.core.atoms.AbstractConsumable;
import com.radixdlt.client.core.atoms.AtomFeeConsumable;
import com.radixdlt.client.core.atoms.Consumable;
import com.radixdlt.client.core.atoms.RadixHash;
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
			.scanWith(HashMap<RadixHash, AbstractConsumable>::new, (map, p) -> {
				HashMap<RadixHash, AbstractConsumable> newMap = new HashMap<>(map);
				newMap.put(p.getHash(), p);
				return newMap;
			})
			.map(map -> map.values().stream()
				.filter(AbstractConsumable::isConsumable)
				.map(AbstractConsumable::getAsConsumable)
				.collect(Collectors.toList())
			)
			.debounce(1000, TimeUnit.MILLISECONDS)
			.map(consumables -> {
				long balanceInSubUnits = consumables.stream().mapToLong(Consumable::getSignedQuantity).sum();
				Amount balance = Amount.subUnitsOf(balanceInSubUnits, Asset.TEST);
				return new AddressTokenState(balance, consumables);
			})
			.replay(1)
			.autoConnect();
	}

	public Observable<AddressTokenState> getState() {
		return state;
	}
}
