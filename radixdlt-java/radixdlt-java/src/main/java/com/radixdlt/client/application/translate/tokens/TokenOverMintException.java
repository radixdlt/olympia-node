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

import com.radixdlt.client.application.translate.StageActionException;
import com.radixdlt.identifiers.REAddr;
import java.math.BigDecimal;
import java.util.Objects;

public final class TokenOverMintException extends StageActionException {
	private final REAddr tokenDefinitionReference;
	private final BigDecimal maxAmount;
	private final BigDecimal currentAmount;
	private final BigDecimal requestedAmount;

	public TokenOverMintException(
		REAddr tokenDefinitionReference,
		BigDecimal maxAmount,
		BigDecimal currentAmount,
		BigDecimal requestedAmount
	) {
		super("Mint amount of " + requestedAmount + " would overflow maximum of " + maxAmount + ". Current is " + currentAmount + ".");
		this.tokenDefinitionReference = Objects.requireNonNull(tokenDefinitionReference);
		this.maxAmount = Objects.requireNonNull(maxAmount);
		this.currentAmount = Objects.requireNonNull(currentAmount);
		this.requestedAmount = Objects.requireNonNull(requestedAmount);
	}

	public BigDecimal getMaxAmount() {
		return maxAmount;
	}

	public BigDecimal getCurrentAmount() {
		return currentAmount;
	}

	@Override
	public boolean equals(Object obj) {
		if (!(obj instanceof TokenOverMintException)) {
			return false;
		}

		TokenOverMintException o = (TokenOverMintException) obj;
		return this.tokenDefinitionReference.equals(o.tokenDefinitionReference)
			&& this.maxAmount.compareTo(o.maxAmount) == 0
			&& this.currentAmount.compareTo(o.currentAmount) == 0
			&& this.requestedAmount.compareTo(o.requestedAmount) == 0;
	}

	@Override
	public int hashCode() {
		return this.getMessage().hashCode();
	}
}
