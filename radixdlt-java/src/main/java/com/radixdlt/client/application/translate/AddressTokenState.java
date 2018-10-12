package com.radixdlt.client.application.translate;

import com.radixdlt.client.core.atoms.TokenRef;
import com.radixdlt.client.core.atoms.particles.Consumable;
import java.util.List;
import java.util.Map;

public class AddressTokenState {
	private final Map<TokenRef, Long> balance;
	private final Map<TokenRef, List<Consumable>> unconsumedConsumables;

	public AddressTokenState(Map<TokenRef, Long> balance, Map<TokenRef, List<Consumable>> unconsumedConsumables) {
		this.balance = balance;
		this.unconsumedConsumables = unconsumedConsumables;
	}

	public Map<TokenRef, Long> getBalance() {
		return balance;
	}

	public Map<TokenRef, List<Consumable>> getUnconsumedConsumables() {
		return unconsumedConsumables;
	}
}
