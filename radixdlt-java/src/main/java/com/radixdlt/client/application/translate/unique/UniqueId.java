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

package com.radixdlt.client.application.translate.unique;

import com.radixdlt.identifiers.RadixAddress;
import java.util.Objects;

public final class UniqueId {
	private final RadixAddress address;
	private final String unique;

	public UniqueId(RadixAddress address, String unique) {
		this.address = Objects.requireNonNull(address);
		this.unique = Objects.requireNonNull(unique);
	}

	public RadixAddress getAddress() {
		return address;
	}

	public String getUnique() {
		return unique;
	}

	@Override
	public String toString() {
		return this.getAddress().toString() + "/" + this.getUnique();
	}

	@Override
	public int hashCode() {
		return Objects.hash(address, unique);
	}

	@Override
	public boolean equals(Object obj) {
		if (!(obj instanceof UniqueId)) {
			return false;
		}

		UniqueId uniqueId = (UniqueId) obj;
		return uniqueId.address.equals(this.address) && uniqueId.unique.equals(this.unique);
	}
}
