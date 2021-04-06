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

import com.radixdlt.identifiers.AID;
import com.radixdlt.identifiers.RadixAddress;
import com.radixdlt.utils.functional.Result;

import java.util.List;
import java.util.Optional;

public class ParsedTx {
	private final AID id;
	private final List<ParticleWithSpin> particles;
	private final Optional<String> message;
	private final RadixAddress creator;

	public ParsedTx(AID id, List<ParticleWithSpin> particles, Optional<String> message, RadixAddress creator) {
		this.id = id;
		this.particles = particles;
		this.message = message;
		this.creator = creator;
	}

	public static Result<ParsedTx> create(
		AID id, List<ParticleWithSpin> particles, Optional<String> message, Optional<RadixAddress> creator
	) {
		return creator.map(author -> Result.ok(new ParsedTx(id, particles, message, author)))
			.orElseGet(() -> Result.fail("No transaction author"));
	}

	public AID getId() {
		return id;
	}

	public List<ParticleWithSpin> getParticles() {
		return particles;
	}

	public Optional<String> getMessage() {
		return message;
	}

	public RadixAddress getCreator() {
		return creator;
	}
}
