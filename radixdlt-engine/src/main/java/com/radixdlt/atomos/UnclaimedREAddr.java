/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 */

package com.radixdlt.atomos;

import java.util.Objects;

import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.identifiers.REAddr;

public final class UnclaimedREAddr implements Particle {
	private final REAddr addr;

	public UnclaimedREAddr(REAddr addr) {
		this.addr = addr;
	}

	public REAddr getAddr() {
		return addr;
	}

	public boolean allow(ECPublicKey publicKey, byte[] arg) {
		return addr.allowToClaimAddress(publicKey, arg);
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.addr);
	}

	@Override
	public boolean equals(Object obj) {
		if (!(obj instanceof UnclaimedREAddr)) {
			return false;
		}
		final var that = (UnclaimedREAddr) obj;
		return Objects.equals(this.addr, that.addr);
	}

	@Override
	public String toString() {
		return String.format("%s[(%s)]", getClass().getSimpleName(), addr);
	}
}
