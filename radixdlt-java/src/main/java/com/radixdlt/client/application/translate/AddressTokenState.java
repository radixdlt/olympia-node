package com.radixdlt.client.application.translate;

import com.radixdlt.client.core.address.EUID;
import com.radixdlt.client.core.atoms.particles.Consumable;
import java.util.List;
import java.util.Map;

public class AddressTokenState {
	private final Map<EUID, Long> balance;
	private final Map<EUID, List<Consumable>> unconsumedConsumables;

	public AddressTokenState(Map<EUID, Long> balance, Map<EUID, List<Consumable>> unconsumedConsumables) {
		this.balance = balance;
		this.unconsumedConsumables = unconsumedConsumables;
	}

	public Map<EUID, Long> getBalance() {
		return balance;
	}

	public Map<EUID, List<Consumable>> getUnconsumedConsumables() {
		return unconsumedConsumables;
	}
}
