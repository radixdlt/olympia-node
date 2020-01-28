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

package com.radixdlt.client.core.atoms.particles;

import com.radixdlt.client.atommodel.accounts.RadixAddress;
import java.util.Objects;

/**
 * A Radix resource identifier is a human readable index into the Ledger which points to a name state machine
 * instance.
 */
public final class RRI {
	// TODO: Will replace this with shardable at some point
	private RadixAddress address;
	private String name;

	RRI(RadixAddress address, String name) {
		this.address = address;
		this.name = name;
	}

	public static RRI of(RadixAddress address, String unique) {
		return new RRI(address, unique);
	}

	public RadixAddress getAddress() {
		return address;
	}

	public String getName() {
		return name;
	}

	public static RRI fromString(String s) {
		String[] split = s.split("/");
		if (split.length < 2) {
			throw new IllegalArgumentException("RRI must be of the format /:address/:name");
		}

		RadixAddress address = RadixAddress.from(split[1]);
		String unique = s.substring(split[1].length() + 2);

		return new RRI(address, unique);
	}

	@Override
	public String toString() {
		return "/" + address.toString() + "/" + name;
	}

	@Override
	public int hashCode() {
		return Objects.hash(address, name);
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof RRI)) {
			return false;
		}

		RRI rri = (RRI) o;
		return Objects.equals(address, rri.address)
			&& Objects.equals(name, rri.name);
	}
}
