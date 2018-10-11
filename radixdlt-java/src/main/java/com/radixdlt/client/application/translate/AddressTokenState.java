package com.radixdlt.client.application.translate;

import com.radixdlt.client.core.atoms.particles.Consumable;
import java.util.List;
import java.util.Map;

public class AddressTokenState {
	private final Map<String, Long> balance;
	private final Map<String, List<Consumable>> unconsumedConsumables;

	public AddressTokenState(Map<String, Long> balance, Map<String, List<Consumable>> unconsumedConsumables) {
		this.balance = balance;
		this.unconsumedConsumables = unconsumedConsumables;
	}

	public Map<String, Long> getBalance() {
		return balance;
	}

	public Map<String, List<Consumable>> getUnconsumedConsumables() {
		return unconsumedConsumables;
	}
}
