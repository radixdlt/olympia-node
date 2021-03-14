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
import com.google.inject.name.Named;
import com.radixdlt.atommodel.Atom;
import com.radixdlt.crypto.Hasher;

import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.universe.UniverseConfig;
import com.radixdlt.universe.UniverseConfiguration;
import com.radixdlt.universe.Universe;
import com.radixdlt.utils.Pair;

import java.util.Objects;

public final class RadixUniverseBuilder {
	private final Hasher hasher;
	private final long universeTimestamp;
	private final ECKeyPair universeKey;
	private final Provider<Atom> genesisAtomProvider;
	private final UniverseConfiguration universeConfiguration;

	@Inject
	public RadixUniverseBuilder(
		@Named("universeKey") ECKeyPair universeKey,
		@UniverseConfig long universeTimestamp,
		UniverseConfiguration universeConfiguration,
		Provider<Atom> genesisAtomProvider,
		Hasher hasher
	) {
		this.universeKey = Objects.requireNonNull(universeKey);
		this.universeTimestamp = universeTimestamp;
		this.universeConfiguration = universeConfiguration;
		this.genesisAtomProvider = Objects.requireNonNull(genesisAtomProvider);
		this.hasher = Objects.requireNonNull(hasher);
	}

	public Pair<ECKeyPair, Universe> build() {
		final var port = universeConfiguration.getPort();
		final var name = universeConfiguration.getName();
		final var description = universeConfiguration.getDescription();
		final var universeAtom = genesisAtomProvider.get();

		final var universe = Universe.newBuilder()
			.port(port)
			.name(name)
			.description(description)
			.type(this.universeConfiguration.getUniverseType())
			.timestamp(this.universeTimestamp)
			.creator(this.universeKey.getPublicKey())
			.setAtom(universeAtom)
			.build();

		Universe.sign(universe, this.universeKey, this.hasher);
		if (!Universe.verify(universe, this.universeKey.getPublicKey(), this.hasher)) {
			throw new IllegalStateException(
				String.format("Signature verification failed for %s universe with key %s", name, this.universeKey)
			);
		}
		return Pair.of(this.universeKey, universe);
	}
}
