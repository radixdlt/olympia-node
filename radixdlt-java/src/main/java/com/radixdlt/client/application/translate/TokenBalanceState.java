package com.radixdlt.client.application.translate;

import com.radixdlt.client.core.atoms.RadixHash;
import com.radixdlt.client.core.atoms.TokenClassReference;
import com.radixdlt.client.core.atoms.particles.SpunParticle;
import com.radixdlt.client.core.atoms.particles.TransferParticle;
import com.radixdlt.client.core.atoms.particles.Spin;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Stream;

/**
 * All the token balances at an address at a given point in time.
 */
public class TokenBalanceState {
	public static class Balance {
		private final Long balance;
		private final Map<RadixHash, SpunParticle<TransferParticle>> consumables;

		private Balance(Long balance, Map<RadixHash, SpunParticle<TransferParticle>> consumables) {
			this.balance = balance;
			this.consumables = consumables;
		}

		private Balance(Long balance, SpunParticle<TransferParticle> s) {
			this.balance = balance;
			this.consumables = Collections.singletonMap(RadixHash.of(s.getParticle().getDson()), s);
		}

		public static Balance empty() {
			return new Balance(0L, Collections.emptyMap());
		}

		public BigDecimal getAmount() {
			return TokenClassReference.subUnitsToDecimal(balance);
		}

		public Stream<TransferParticle> unconsumedConsumables() {
			return consumables.entrySet().stream()
				.map(Entry::getValue)
				.filter(c -> c.getSpin() == Spin.UP)
				.map(SpunParticle::getParticle);
		}

		public static Balance merge(Balance balance, SpunParticle<TransferParticle> s) {
			TransferParticle transferParticle = s.getParticle();
			Map<RadixHash, SpunParticle<TransferParticle>> newMap = new HashMap<>(balance.consumables);
			newMap.put(RadixHash.of(transferParticle.getDson()), s);
			Long newBalance = balance.balance + ((s.getSpin() == Spin.UP) ? 1 : -1) * transferParticle.getAmount();
			return new Balance(newBalance, newMap);
		}
	}

	private final Map<TokenClassReference, Balance> balance;

	public TokenBalanceState() {
		this.balance = Collections.emptyMap();
	}

	public TokenBalanceState(Map<TokenClassReference, Balance> balance) {
		this.balance = balance;
	}

	public Map<TokenClassReference, Balance> getBalance() {
		return Collections.unmodifiableMap(balance);
	}

	public static TokenBalanceState merge(TokenBalanceState state, SpunParticle<TransferParticle> s) {
		HashMap<TokenClassReference, Balance> balance = new HashMap<>(state.balance);
		TransferParticle transferParticle = s.getParticle();
		balance.merge(
				transferParticle.getTokenClassReference(),
				new Balance(transferParticle.getAmount(), s),
				(bal1, bal2) -> Balance.merge(bal1, s)
		);

		return new TokenBalanceState(balance);
	}
}
