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
 * An action for redelegating staked tokens from one delegate to another
 */
public class RedelegateStakedTokensAction implements Action {
	private final RadixAddress from;
	private final RadixAddress oldDelegate;
	private final RadixAddress newDelegate;
	private final RRI rri;
	private final BigDecimal amount;

	private RedelegateStakedTokensAction(
		BigDecimal amount,
		RRI rri,
		RadixAddress from,
		RadixAddress oldDelegate,
		RadixAddress newDelegate
	) {
		if (amount.stripTrailingZeros().scale() > TokenUnitConversions.getTokenScale()) {
			throw new IllegalArgumentException("Amount must scale by " + TokenUnitConversions.getTokenScale());
		}

		this.from = from;
		this.oldDelegate = oldDelegate;
		this.newDelegate = newDelegate;
		this.rri = rri;
		this.amount = amount;
	}

	public static RedelegateStakedTokensAction create(
		BigDecimal amount,
		RRI rri,
		RadixAddress from,
		RadixAddress oldDelegate,
		RadixAddress newDelegate
	) {
		return new RedelegateStakedTokensAction(
			amount,
			rri,
			from,
			oldDelegate,
			newDelegate
		);
	}

	public RadixAddress getFrom() {
		return from;
	}

	public RadixAddress getOldDelegate() {
		return oldDelegate;
	}

	public RadixAddress getNewDelegate() {
		return newDelegate;
	}

	public RRI getRRI() {
		return rri;
	}

	public BigDecimal getAmount() {
		return amount;
	}

	@Override
	public String toString() {
		return String.format(
			"REDELEGATE STAKED TOKENS %s %s OF %s FROM %s TO %s",
			amount, rri.getName(), from, oldDelegate, newDelegate
		);
	}
}
