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

import com.radixdlt.identifiers.RadixAddress;
import com.radixdlt.identifiers.RRI;
import java.math.BigDecimal;
import java.util.Objects;

import com.radixdlt.client.application.translate.Action;

public final class MintTokensAction implements Action {
	private final RRI rri;
	private final RadixAddress address;
	private final BigDecimal amount;

	private MintTokensAction(RRI rri, RadixAddress address, BigDecimal amount) {
		this.rri = Objects.requireNonNull(rri);
		this.address = Objects.requireNonNull(address);
		this.amount = Objects.requireNonNull(amount);
	}

	public static MintTokensAction create(
		RRI tokenDefRef,
		RadixAddress address,
		BigDecimal amount
	) {
		return new MintTokensAction(tokenDefRef, address, amount);
	}

	public RadixAddress getAddress() {
		return address;
	}

	public RRI getRRI() {
		return rri;
	}

	public BigDecimal getAmount() {
		return amount;
	}

	@Override
	public String toString() {
		return "MINT TOKEN " + amount + " " + rri;
	}
}
