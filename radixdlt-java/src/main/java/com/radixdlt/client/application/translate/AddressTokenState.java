package com.radixdlt.client.application.translate;

import com.radixdlt.client.core.atoms.TokenReference;
import com.radixdlt.client.core.atoms.particles.Consumable;
import java.util.List;
import java.util.Map;

public class AddressTokenState {
	private final Map<TokenReference, Long> balance;
	private final Map<TokenReference, List<Consumable>> unconsumedConsumables;

	public AddressTokenState(Map<TokenReference, Long> balance, Map<TokenReference, List<Consumable>> unconsumedConsumables) {
		this.balance = balance;
		this.unconsumedConsumables = unconsumedConsumables;
	}

	public Map<TokenReference, Long> getBalance() {
		return balance;
	}

	public Map<TokenReference, List<Consumable>> getUnconsumedConsumables() {
		return unconsumedConsumables;
	}
}
