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

package com.radixdlt.client.store;

import com.radixdlt.atom.Txn;
import com.radixdlt.identifiers.AID;
import com.radixdlt.identifiers.RadixAddress;

import java.util.List;
import java.util.Optional;

public class ParsedTx {
	private final Txn txn;
	private final List<ParticleWithSpin> particles;
	private final Optional<String> message;
	private final Optional<RadixAddress> creator;

	private ParsedTx(Txn txn, List<ParticleWithSpin> particles, Optional<String> message, Optional<RadixAddress> creator) {
		this.txn = txn;
		this.particles = particles;
		this.message = message;
		this.creator = creator;
	}

	public static ParsedTx create(
		Txn txn, List<ParticleWithSpin> particles, Optional<String> message, Optional<RadixAddress> creator
	) {
		return new ParsedTx(txn, particles, message, creator);
	}

	public AID getId() {
		return txn.getId();
	}

	public List<ParticleWithSpin> getParticles() {
		return particles;
	}

	public Optional<String> getMessage() {
		return message;
	}

	public Optional<RadixAddress> getCreator() {
		return creator;
	}
}
