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
import com.radixdlt.atommodel.tokens.StakedTokensParticle;
import com.radixdlt.identifiers.RRI;
import com.radixdlt.identifiers.RadixAddress;
import com.radixdlt.utils.Pair;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * The tokens staked to an address at a given point in time.
 */
public class StakedTokenBalanceState implements ApplicationState {
	private final ImmutableMap<Pair<RadixAddress, RRI>, BigDecimal> balance;

	public StakedTokenBalanceState() {
		this.balance = ImmutableMap.of();
	}

	private StakedTokenBalanceState(Map<Pair<RadixAddress, RRI>, BigDecimal> balance) {
		this.balance = ImmutableMap.copyOf(balance);
	}

	public Map<Pair<RadixAddress, RRI>, BigDecimal> getBalance() {
		return this.balance;
	}

	public static StakedTokenBalanceState empty() {
		return new StakedTokenBalanceState();
	}

	public static StakedTokenBalanceState combine(StakedTokenBalanceState state0, StakedTokenBalanceState state1) {
		if (state0 == state1) {
			return state0;
		}

		final var balance = new HashMap<>(state0.balance);
		state1.balance.forEach((key, bal) -> balance.merge(key, bal, BigDecimal::add));
		return new StakedTokenBalanceState(balance);
	}

	public static StakedTokenBalanceState merge(StakedTokenBalanceState state, StakedTokensParticle particle) {
		final var balance = new HashMap<>(state.balance);
		final var key = Pair.of(particle.getDelegateAddress(), particle.getTokDefRef());
		final var amount = TokenUnitConversions.subunitsToUnits(particle.getAmount());
		balance.merge(key, amount, BigDecimal::add);
		return new StakedTokenBalanceState(balance);
	}

	@Override
	public String toString() {
		return balance.toString();
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof StakedTokenBalanceState)) {
			return false;
		}

		StakedTokenBalanceState s = (StakedTokenBalanceState) o;

		return s.balance.equals(balance);
	}

	@Override
	public int hashCode() {
		return balance.hashCode();
	}
}
