package com.radixdlt.client.application.translate.tokens;

import com.google.common.collect.ImmutableMap;
import com.radixdlt.client.application.translate.ApplicationState;
import com.radixdlt.client.atommodel.tokens.TransferrableTokensParticle;
import com.radixdlt.client.core.atoms.particles.RRI;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import org.radix.utils.UInt256s;

/**
 * All the token balances at an address at a given point in time.
 */
public class TokenBalanceState implements ApplicationState {
	public static class Balance {
		private final BigInteger balance;
		private final BigInteger granularity;

		private Balance(BigInteger balance, BigInteger granularity) {
			this.balance = balance;
			this.granularity = granularity;
		}

		public static Balance empty(BigInteger granularity) {
			return new Balance(BigInteger.ZERO, granularity);
		}

		public BigDecimal getAmount() {
			return TokenUnitConversions.subunitsToUnits(balance);
		}

		public BigDecimal getGranularity() {
			return TokenUnitConversions.subunitsToUnits(granularity);
		}

		public static Balance combine(Balance balance0, Balance balance1) {
			return new Balance(
				balance0.balance.add(balance1.balance),
				balance0.granularity
			);
		}

		public static Balance merge(Balance balance, TransferrableTokensParticle tokens) {
			final BigInteger amount = UInt256s.toBigInteger(tokens.getAmount());
			final BigInteger newBalance = balance.balance.add(amount);
			return new Balance(newBalance, balance.granularity);
		}

		@Override
		public int hashCode() {
			return Objects.hash(balance, granularity);
		}

		@Override
		public boolean equals(Object o) {
			if (!(o instanceof Balance)) {
				return false;
			}

			Balance b = (Balance) o;
			return Objects.equals(b.balance, balance)
				&& Objects.equals(b.granularity, granularity);
		}

		@Override
		public String toString() {
			return "{BAL:" + getAmount().toString() + "}";
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
			new Balance(amount, granularity),
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
