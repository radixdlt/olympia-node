/*
 * (C) Copyright 2021 Radix DLT Ltd
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

import java.util.Objects;

import com.radixdlt.identifiers.AID;

/**
 * An application layer object representing an atom message.
 */
public final class PlaintextMessage {
	private final AID aid;
	private final String message;

	public PlaintextMessage(AID aid, String message) {
		this.aid = Objects.requireNonNull(aid);
		this.message = Objects.requireNonNull(message);
	}

	/**
	 * Returns the ID of the atom the message was found in.
	 *
	 * @return the atom ID corresponding with the message
	 */
	public AID getAtomId() {
		return this.aid;
	}

	/**
	 * Returns the message.
	 *
	 * @return the message
	 */
	public String getMessage() {
		return this.message;
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.aid, this.message);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj instanceof PlaintextMessage) {
			PlaintextMessage that = (PlaintextMessage) obj;
			return Objects.equals(this.aid, that.aid) && Objects.equals(this.message, that.message);
		}
		return false;
	}

	@Override
	public String toString() {
		return String.format("%s[%s:%s]", getClass().getSimpleName(), this.aid, this.message);
	}
}
