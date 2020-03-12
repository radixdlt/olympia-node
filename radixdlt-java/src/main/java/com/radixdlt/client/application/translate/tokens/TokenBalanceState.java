package com.radixdlt.client.application.translate.tokens;

import com.google.common.collect.ImmutableMap;
import com.radixdlt.client.application.translate.ApplicationState;
import com.radixdlt.client.atommodel.tokens.TransferrableTokensParticle;
import com.radixdlt.identifiers.RRI;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * All the token balances at an address at a given point in time.
 */
public class TokenBalanceState implements ApplicationState {
	private final ImmutableMap<RRI, BigDecimal> balance;

	public TokenBalanceState() {
		this.balance = ImmutableMap.of();
	}

	public TokenBalanceState(Map<RRI, BigDecimal> balance) {
		this.balance = ImmutableMap.copyOf(balance);
	}

	public Map<RRI, BigDecimal> getBalance() {
		return balance;
	}

	public static TokenBalanceState combine(TokenBalanceState state0, TokenBalanceState state1) {
		if (state0 == state1) {
			return state0;
		}

		HashMap<RRI, BigDecimal> balance = new HashMap<>(state0.balance);
		state1.balance.forEach((rri, bal) -> balance.merge(rri, bal, BigDecimal::add));
		return new TokenBalanceState(balance);
	}

	public static TokenBalanceState merge(TokenBalanceState state, TransferrableTokensParticle tokens) {
		HashMap<RRI, BigDecimal> balance = new HashMap<>(state.balance);
		BigDecimal amount = TokenUnitConversions.subunitsToUnits(tokens.getAmount());
		balance.merge(
			tokens.getTokenDefinitionReference(),
			amount,
			BigDecimal::add
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
