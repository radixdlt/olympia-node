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
import com.radixdlt.client.application.translate.ApplicationState;
import com.radixdlt.identifiers.Rri;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * The tokens delegated to an address at a given point in time.
 */
public class DelegatedTokenBalanceState implements ApplicationState {
	private final ImmutableMap<Rri, BigDecimal> balance;

	public DelegatedTokenBalanceState() {
		this.balance = ImmutableMap.of();
	}

	private DelegatedTokenBalanceState(Map<Rri, BigDecimal> balance) {
		this.balance = ImmutableMap.copyOf(balance);
	}

	public Map<Rri, BigDecimal> getBalance() {
		return balance;
	}

	public static DelegatedTokenBalanceState empty() {
		return new DelegatedTokenBalanceState();
	}

	public static DelegatedTokenBalanceState combine(DelegatedTokenBalanceState state0, DelegatedTokenBalanceState state1) {
		if (state0 == state1) {
			return state0;
		}

		HashMap<Rri, BigDecimal> balance = new HashMap<>(state0.balance);
		state1.balance.forEach((rri, bal) -> balance.merge(rri, bal, BigDecimal::add));
		return new DelegatedTokenBalanceState(balance);
	}

	public static DelegatedTokenBalanceState merge(DelegatedTokenBalanceState state, Rri tokenRri, BigDecimal amount) {
		HashMap<Rri, BigDecimal> balance = new HashMap<>(state.balance);
		balance.merge(tokenRri, amount, BigDecimal::add);
		return new DelegatedTokenBalanceState(balance);
	}

	@Override
	public String toString() {
		return balance.toString();
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof DelegatedTokenBalanceState)) {
			return false;
		}

		DelegatedTokenBalanceState s = (DelegatedTokenBalanceState) o;

		return s.balance.equals(balance);
	}

	@Override
	public int hashCode() {
		return balance.hashCode();
	}
}
