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

package com.radixdlt.client.application.translate.data;

import com.radixdlt.client.application.translate.Action;
import com.radixdlt.identifiers.RadixAddress;
import java.util.Objects;

/**
 * An Action object which sends a data transaction from one account to another.
 */
public final class SendMessageAction implements Action {
	private final RadixAddress from;
	private final RadixAddress to;
	private final byte[] data;
	private final boolean encrypt;

	private SendMessageAction(RadixAddress from, RadixAddress to, byte[] data, boolean encrypt) {
		this.from = Objects.requireNonNull(from);
		this.data = Objects.requireNonNull(data);
		this.to = Objects.requireNonNull(to);
		this.encrypt = encrypt;
	}

	public static SendMessageAction create(RadixAddress from, RadixAddress to, byte[] data, boolean encrypt) {
		return new SendMessageAction(from, to, data, encrypt);
	}

	public boolean encrypt() {
		return encrypt;
	}

	public byte[] getData() {
		return data;
	}

	public RadixAddress getTo() {
		return to;
	}

	public RadixAddress getFrom() {
		return from;
	}

	@Override
	public String toString() {
		return "SEND MESSAGE FROM " + from + " TO " + to;
	}
}
