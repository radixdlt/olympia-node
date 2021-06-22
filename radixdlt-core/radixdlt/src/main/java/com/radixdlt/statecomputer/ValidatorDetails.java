/*
 * (C) Copyright 2021 Radix DLT Ltd
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

package com.radixdlt.statecomputer;

import com.radixdlt.atommodel.validators.state.ValidatorParticle;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.utils.UInt256;

public final class ValidatorDetails {
	private final ECPublicKey key;
	private final REAddr owner;
	private final String name;
	private final String url;
	private final UInt256 stake;

	private ValidatorDetails(ECPublicKey key, REAddr owner, String name, String url, UInt256 stake) {
		this.key = key;
		this.name = name;
		this.owner = owner;
		this.url = url;
		this.stake = stake;
	}

	public static ValidatorDetails fromParticle(ValidatorParticle particle, REAddr owner, UInt256 stake) {
		return new ValidatorDetails(particle.getKey(), owner, particle.getName(), particle.getUrl(), stake);
	}

	public REAddr getOwner() {
		return owner;
	}

	public ECPublicKey getKey() {
		return key;
	}

	public String getName() {
		return name;
	}

	public String getUrl() {
		return url;
	}

	public UInt256 getStake() {
		return stake;
	}
}
