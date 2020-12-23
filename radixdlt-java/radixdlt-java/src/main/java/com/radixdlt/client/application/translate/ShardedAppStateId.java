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

package com.radixdlt.client.application.translate;

import com.radixdlt.identifiers.RadixAddress;
import java.util.Objects;

/**
 * Identifier for application state for a particular address.
 */
public final class ShardedAppStateId {
	private final Class<? extends ApplicationState> stateClass;
	private final RadixAddress address;

	private ShardedAppStateId(Class<? extends ApplicationState> stateClass, RadixAddress address) {
		Objects.requireNonNull(stateClass);
		Objects.requireNonNull(address);

		this.stateClass = stateClass;
		this.address = address;
	}

	public static ShardedAppStateId of(Class<? extends ApplicationState> stateClass, RadixAddress address) {
		return new ShardedAppStateId(stateClass, address);
	}

	/**
	 * Retrieves the type of application state needed for this requirement
	 *
	 * @return the type of application state
	 */
	public Class<? extends ApplicationState> stateClass() {
		return this.stateClass;
	}

	/**
	 * Retrieves the shardable address which needs to be queried to construct the application state
	 *
	 * @return the shardable address
	 */
	public RadixAddress address() {
		return this.address;
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof ShardedAppStateId)) {
			return false;
		}

		ShardedAppStateId r = (ShardedAppStateId) o;
		return r.stateClass.equals(stateClass) && r.address.equals(address);
	}

	@Override
	public int hashCode() {
		return Objects.hash(stateClass, address);
	}

	@Override
	public String toString() {
		return address + "/" + stateClass.getSimpleName();
	}
}

