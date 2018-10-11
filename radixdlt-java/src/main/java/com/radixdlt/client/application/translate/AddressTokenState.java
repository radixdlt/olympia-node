package com.radixdlt.client.application.translate;

import com.radixdlt.client.core.atoms.Token;
import com.radixdlt.client.core.atoms.particles.Consumable;
import java.util.List;
import java.util.Map;

public class AddressTokenState {
	private final Map<Token, Long> balance;
	private final Map<Token, List<Consumable>> unconsumedConsumables;

	public AddressTokenState(Map<Token, Long> balance, Map<Token, List<Consumable>> unconsumedConsumables) {
		this.balance = balance;
		this.unconsumedConsumables = unconsumedConsumables;
	}

	public Map<Token, Long> getBalance() {
		return balance;
	}

	public Map<Token, List<Consumable>> getUnconsumedConsumables() {
		return unconsumedConsumables;
	}
}
