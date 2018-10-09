package com.radixdlt.client.application.translate;

import com.radixdlt.client.assets.Amount;
import com.radixdlt.client.core.atoms.Consumable;
import java.util.List;

public class AddressTokenState {
	private final Amount balance;
	private final List<Consumable> unconsumedConsumables;

	public AddressTokenState(Amount balance, List<Consumable> unconsumedConsumables) {
		this.balance = balance;
		this.unconsumedConsumables = unconsumedConsumables;
	}

	public Amount getBalance() {
		return balance;
	}

	public List<Consumable> getUnconsumedConsumables() {
		return unconsumedConsumables;
	}
}
