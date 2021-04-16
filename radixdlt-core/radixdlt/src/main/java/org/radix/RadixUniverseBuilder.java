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

package org.radix;

import com.google.inject.Inject;
import com.google.inject.Provider;

import com.radixdlt.ledger.VerifiedTxnsAndProof;
import com.radixdlt.universe.UniverseConfiguration;
import com.radixdlt.universe.Universe;

import java.util.Objects;

public final class RadixUniverseBuilder {
	private final Provider<VerifiedTxnsAndProof> genesisProvider;
	private final UniverseConfiguration universeConfiguration;

	@Inject
	public RadixUniverseBuilder(
		UniverseConfiguration universeConfiguration,
		Provider<VerifiedTxnsAndProof> genesisProvider
	) {
		this.universeConfiguration = universeConfiguration;
		this.genesisProvider = Objects.requireNonNull(genesisProvider);
	}

	public Universe build() {
		final var name = universeConfiguration.getName();
		final var description = universeConfiguration.getDescription();
		final var universeAtom = genesisProvider.get();

		return Universe.newBuilder()
			.name(name)
			.description(description)
			.type(this.universeConfiguration.getUniverseType())
			.setTxnsAndProof(universeAtom)
			.build();
	}
}
