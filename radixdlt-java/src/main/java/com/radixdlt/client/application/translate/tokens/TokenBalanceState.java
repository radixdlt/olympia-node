package com.radixdlt.client.application.translate.tokens;

import com.google.common.collect.ImmutableMap;
import com.radixdlt.client.application.translate.ApplicationState;
import com.radixdlt.client.atommodel.tokens.TransferrableTokensParticle;
import com.radixdlt.client.core.atoms.RadixHash;
import com.radixdlt.client.core.atoms.particles.RRI;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Stream;

import org.radix.utils.UInt256s;

/**
 * All the token balances at an address at a given point in time.
 */
public class TokenBalanceState implements ApplicationState {
	public static class Balance {
		private final BigInteger balance;
		private final BigInteger granularity;
		private final ImmutableMap<RadixHash, TransferrableTokensParticle> consumables;

		private Balance(BigInteger balance, BigInteger granularity, Map<RadixHash, TransferrableTokensParticle> consumables) {
			this.balance = balance;
			this.granularity = granularity;
			this.consumables = ImmutableMap.copyOf(consumables);
		}

		private Balance(BigInteger balance, BigInteger granularity, TransferrableTokensParticle s) {
			this(balance, granularity, Collections.singletonMap(s.getHash(), s));
		}

		public static Balance empty(BigInteger granularity) {
			return new Balance(BigInteger.ZERO, granularity, Collections.emptyMap());
		}

		public BigDecimal getAmount() {
			return TokenUnitConversions.subunitsToUnits(balance);
		}

		public BigDecimal getGranularity() {
			return TokenUnitConversions.subunitsToUnits(granularity);
		}

		public Stream<TransferrableTokensParticle> unconsumedTransferrable() {
			return consumables.entrySet().stream()
				.map(Entry::getValue);
		}

		public static Balance combine(Balance balance0, Balance balance1) {
			return new Balance(
				balance0.balance.add(balance1.balance),
				balance0.granularity,
				new ImmutableMap.Builder<RadixHash, TransferrableTokensParticle>()
					.putAll(balance0.consumables)
					.putAll(balance1.consumables)
					.build()
			);
		}

		public static Balance merge(Balance balance, TransferrableTokensParticle tokens) {
			Map<RadixHash, TransferrableTokensParticle> newMap = new HashMap<>(balance.consumables);

			final BigInteger amount = UInt256s.toBigInteger(tokens.getAmount());
			newMap.put(tokens.getHash(), tokens);

			BigInteger newBalance = balance.balance.add(amount);

			return new Balance(newBalance, balance.granularity, newMap);
		}

		@Override
		public int hashCode() {
			return consumables.hashCode();
		}

		@Override
		public boolean equals(Object o) {
			if (!(o instanceof Balance)) {
				return false;
			}

			Balance b = (Balance) o;
			return b.consumables.keySet().equals(consumables.keySet());
		}

		@Override
		public String toString() {
			return "{BAL:" + getAmount().toString() + " CONSUMABLES:" + consumables.size()
				+ "("
				+ (consumables.size() == 1
					? consumables.keySet().asList().get(0) + " " + consumables.entrySet().asList().get(0).getValue().getAmount()
					: consumables.hashCode())
				+ ")}";
		}
	}

	private final ImmutableMap<RRI, Balance> balance;

	public TokenBalanceState() {
		this.balance = ImmutableMap.of();
	}

	public TokenBalanceState(Map<RRI, Balance> balance) {
		this.balance = ImmutableMap.copyOf(balance);
	}

	public Map<RRI, Balance> getBalance() {
		return Collections.unmodifiableMap(balance);
	}

	public static TokenBalanceState combine(TokenBalanceState state0, TokenBalanceState state1) {
		if (state0 == state1) {
			return state0;
		}

		HashMap<RRI, Balance> balance = new HashMap<>(state0.balance);
		state1.balance.forEach((rri, bal) -> balance.merge(rri, bal, Balance::combine));
		return new TokenBalanceState(balance);
	}

	public static TokenBalanceState merge(TokenBalanceState state, TransferrableTokensParticle tokens) {
		HashMap<RRI, Balance> balance = new HashMap<>(state.balance);
		BigInteger amount = UInt256s.toBigInteger(tokens.getAmount());
		BigInteger granularity = UInt256s.toBigInteger(tokens.getGranularity());
		balance.merge(
			tokens.getTokenDefinitionReference(),
			new Balance(amount, granularity, tokens),
			Balance::combine
		);

		return new TokenBalanceState(balance);
	}

	@Override
	public String toString() {
		return balance.toString();
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof TokenBalanceState)) {
			return false;
		}

		TokenBalanceState s = (TokenBalanceState) o;

		return s.balance.equals(balance);
	}

	@Override
	public int hashCode() {
		return balance.hashCode();
	}
}
