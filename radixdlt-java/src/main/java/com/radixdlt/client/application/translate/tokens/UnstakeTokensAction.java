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

import com.radixdlt.client.application.translate.Action;
import com.radixdlt.identifiers.RRI;
import com.radixdlt.identifiers.RadixAddress;

import java.math.BigDecimal;

/**
 * An action for unstaking staked tokens from an address staked to a certain delegate
 */
public class UnstakeTokensAction implements Action {
	private final RadixAddress from;
	private final RadixAddress delegate;
	private final RRI rri;
	private final BigDecimal amount;

	private UnstakeTokensAction(
		BigDecimal amount,
		RRI rri,
		RadixAddress from,
		RadixAddress delegate
	) {
		if (amount.stripTrailingZeros().scale() > TokenUnitConversions.getTokenScale()) {
			throw new IllegalArgumentException("Amount must scale by " + TokenUnitConversions.getTokenScale());
		}

		this.from = from;
		this.delegate = delegate;
		this.rri = rri;
		this.amount = amount;
	}

	public static UnstakeTokensAction create(
		BigDecimal amount,
		RRI rri,
		RadixAddress from,
		RadixAddress delegate
	) {
		return new UnstakeTokensAction(
			amount,
			rri,
			from,
			delegate
		);
	}

	public RadixAddress getFrom() {
		return from;
	}

	public RadixAddress getDelegate() {
		return delegate;
	}

	public RRI getRRI() {
		return rri;
	}

	public BigDecimal getAmount() {
		return amount;
	}

	@Override
	public String toString() {
		return String.format("UNSTAKE TOKENS %s %s OF %s FROM %s", amount, rri.getName(), from, delegate);
	}
}
