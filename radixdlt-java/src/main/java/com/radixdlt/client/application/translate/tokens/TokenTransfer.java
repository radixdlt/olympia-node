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
import java.util.Optional;
import com.radixdlt.utils.RadixConstants;

public class TokenTransfer {
	private final RadixAddress from;
	private final RadixAddress to;
	private final RRI tokenDefinition;
	private final BigDecimal amount;
	private final byte[] attachment;

	public TokenTransfer(
		RadixAddress from,
		RadixAddress to,
		RRI tokenDefinition,
		BigDecimal amount,
		byte[] attachment
	) {
		this.from = from;
		this.to = to;
		this.tokenDefinition = tokenDefinition;
		this.amount = amount;
		this.attachment = attachment;
	}

	public BigDecimal getAmount() {
		return amount;
	}

	public RadixAddress getFrom() {
		return from;
	}

	public RadixAddress getTo() {
		return to;
	}

	public RRI getTokenClass() {
		return tokenDefinition;
	}

	public Optional<byte[]> getAttachment() {
		return Optional.ofNullable(attachment);
	}

	public Optional<String> getAttachmentAsString() {
		return getAttachment().map(a -> new String(a, RadixConstants.STANDARD_CHARSET));
	}

	@Override
	public String toString() {
		return from + " -> " + to + " " + amount
			+ (attachment == null ? "" : " " + attachment);
	}
}
