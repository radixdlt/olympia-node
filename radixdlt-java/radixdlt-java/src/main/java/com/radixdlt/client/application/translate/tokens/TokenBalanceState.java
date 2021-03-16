/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Permission is hereby granted, free of charge, to any person obtaining a
 * copy of this software and associated documentation files (the “Software”),
 * to deal in the Software without restriction, including without limitation
 * the rights to use, copy, modify, merge, publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS
 * OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL
 * THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER
 * DEALINGS IN THE SOFTWARE.
 */

package com.radixdlt.client.application.translate.tokens;

import com.google.common.collect.ImmutableMap;
import com.radixdlt.application.TokenUnitConversions;
import com.radixdlt.client.application.translate.ApplicationState;
import com.radixdlt.atommodel.tokens.TransferrableTokensParticle;
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
			tokens.getTokDefRef(),
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
