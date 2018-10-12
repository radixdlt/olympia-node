package com.radixdlt.client.application.translate;

import com.radixdlt.client.core.atoms.RadixHash;
import com.radixdlt.client.core.atoms.TokenRef;
import com.radixdlt.client.core.atoms.particles.Consumable;
import com.radixdlt.client.core.atoms.particles.Spin;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Stream;

public class TokenBalanceState {
	public static class Balance {
		private final Long balance;
		private final Map<RadixHash, Consumable> consumables;

		private Balance(Long balance, Map<RadixHash, Consumable> consumables) {
			this.balance = balance;
			this.consumables = consumables;
		}

		private Balance(Long balance, Consumable consumable) {
			this.balance = balance;
			this.consumables = Collections.singletonMap(RadixHash.of(consumable.getDson()), consumable);
		}

		public static Balance empty() {
			return new Balance(0L, Collections.emptyMap());
		}

		public BigDecimal getAmount() {
			return TokenRef.subUnitsToDecimal(balance);
		}

		public Stream<Consumable> unconsumedConsumables() {
			return consumables.entrySet().stream().map(Entry::getValue).filter(c -> c.getSpin() == Spin.UP);
		}

		public static Balance merge(Balance balance, Consumable consumable) {
			Map<RadixHash, Consumable> newMap = new HashMap<>(balance.consumables);
			newMap.put(RadixHash.of(consumable.getDson()), consumable);
			Long newBalance = balance.balance + ((consumable.getSpin() == Spin.UP) ? 1 : -1) * consumable.getAmount();
			return new Balance(newBalance, newMap);
		}
	}

	private final Map<TokenRef, Balance> balance;

	public TokenBalanceState() {
		this.balance = Collections.emptyMap();
	}

	public TokenBalanceState(Map<TokenRef, Balance> balance) {
		this.balance = balance;
	}

	public Map<TokenRef, Balance> getBalance() {
		return Collections.unmodifiableMap(balance);
	}

	public static TokenBalanceState merge(TokenBalanceState state, Consumable consumable) {
		HashMap<TokenRef, Balance> balance = new HashMap<>(state.balance);
		balance.merge(
			consumable.getTokenRef(),
			new Balance(consumable.getAmount(), consumable),
			(bal1, bal2) -> Balance.merge(bal1, consumable)
		);

		return new TokenBalanceState(balance);
	}
}
