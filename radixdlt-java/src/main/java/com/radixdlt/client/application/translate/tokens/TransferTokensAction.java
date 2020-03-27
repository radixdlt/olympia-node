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
import com.radixdlt.identifiers.RadixAddress;
import com.radixdlt.identifiers.RRI;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class TransferTokensAction implements Action {
	private final RadixAddress from;
	private final RadixAddress to;
	private final RRI rri;
	private final Map<String, Object> metaData;
	private final BigDecimal amount;
	private final byte[] attachment;

	private TransferTokensAction(
		RRI rri,
		RadixAddress from,
		RadixAddress to,
		BigDecimal amount,
		byte[] attachment,
		Map<String, Object> metaData
	) {
		if (amount.stripTrailingZeros().scale() > TokenUnitConversions.getTokenScale()) {
			throw new IllegalArgumentException("Amount must scale by " + TokenUnitConversions.getTokenScale());
		}

		this.from = from;
		this.to = to;
		this.rri = rri;
		this.amount = amount;
		this.attachment = attachment;
		this.metaData = metaData;
	}

	public static TransferTokensAction create(
		RRI rri,
		RadixAddress from,
		RadixAddress to,
		BigDecimal amount
	) {
		return new TransferTokensAction(rri, from, to, amount, null, Collections.emptyMap());
	}

	public static TransferTokensAction create(
		RRI rri,
		RadixAddress from,
		RadixAddress to,
		BigDecimal amount,
		byte[] attachment
	) {
		return new TransferTokensAction(rri, from, to, amount, attachment, Collections.emptyMap());
	}

	public static TransferTokensAction create(
		RRI rri,
		RadixAddress from,
		RadixAddress to,
		BigDecimal amount,
		Long timestamp
	) {
		Map<String, Object> metaData = new HashMap<>();
		metaData.put("timestamp", timestamp);

		return new TransferTokensAction(rri, from, to, amount, null, metaData);
	}

	public static TransferTokensAction create(
		RRI rri,
		RadixAddress from,
		RadixAddress to,
		BigDecimal amount,
		byte[] attachment,
		Long timestamp
	) {
		Map<String, Object> metaData = new HashMap<>();
		metaData.put("timestamp", timestamp);

		return new TransferTokensAction(rri, from, to, amount, attachment, metaData);
	}

	public byte[] getAttachment() {
		return attachment;
	}

	public RadixAddress getFrom() {
		return from;
	}

	public RadixAddress getTo() {
		return to;
	}

	public RRI getRRI() {
		return rri;
	}

	public BigDecimal getAmount() {
		return amount;
	}

	public Map<String, Object> getMetaData() {
		return Collections.unmodifiableMap(metaData);
	}

	@Override
	public String toString() {
		return "TRANSFER TOKEN " + amount + " " + rri.getName() + " FROM " + from + " TO " + to;
	}
}
