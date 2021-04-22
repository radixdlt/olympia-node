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

public final class REAddrParticle implements Particle {
	private final REAddr rri;

	public REAddrParticle(REAddr rri) {
		this.rri = rri;
	}

	public REAddr getAddr() {
		return rri;
	}

	public boolean allow(ECPublicKey publicKey, byte[] arg) {
		return rri.allow(publicKey, arg);
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.rri);
	}

	@Override
	public boolean equals(Object obj) {
		if (!(obj instanceof REAddrParticle)) {
			return false;
		}
		final var that = (REAddrParticle) obj;
		return Objects.equals(this.rri, that.rri);
	}

	@Override
	public String toString() {
		return String.format("%s[(%s)]", getClass().getSimpleName(), rri);
	}
}
