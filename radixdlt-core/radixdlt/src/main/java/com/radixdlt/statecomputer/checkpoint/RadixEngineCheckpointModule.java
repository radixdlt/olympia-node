/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 */

package com.radixdlt.statecomputer.checkpoint;

import com.google.common.collect.ImmutableList;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.radixdlt.DefaultSerialization;
import com.radixdlt.atom.Atom;
import com.radixdlt.atommodel.tokens.MutableSupplyTokenDefinitionParticle;
import com.radixdlt.atommodel.tokens.TokenDefinitionUtils;
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.fees.NativeToken;
import com.radixdlt.identifiers.RRI;
import com.radixdlt.serialization.DeserializeException;

import java.util.List;

/**
 * Configures the module in charge of "weak-subjectivity" or checkpoints
 * which the node will always align with
 */
public class RadixEngineCheckpointModule extends AbstractModule {

	public RadixEngineCheckpointModule() {
		// Nothing to do here
	}

	@Provides
	@Singleton // Don't want to recompute on each use
	@NativeToken
	private RRI nativeToken(@Genesis List<Atom> atoms) {
		final String tokenName = TokenDefinitionUtils.getNativeTokenShortCode();
		ImmutableList<RRI> rris = atoms.stream().flatMap(Atom::bootUpInstructions)
			.map(i -> {
				try {
					return DefaultSerialization.getInstance().fromDson(i.getData(), Particle.class);
				} catch (DeserializeException e) {
					throw new IllegalStateException();
				}
			})
			.filter(p -> p instanceof MutableSupplyTokenDefinitionParticle)
			.map(p -> (MutableSupplyTokenDefinitionParticle) p)
			.map(MutableSupplyTokenDefinitionParticle::getRRI)
			.filter(rri -> rri.getName().equals(tokenName))
			.collect(ImmutableList.toImmutableList());
		if (rris.isEmpty()) {
			throw new IllegalStateException("No mutable supply token " + tokenName + " in genesis");
		}
		if (rris.size() > 1) {
			throw new IllegalStateException("More than one mutable supply token " + tokenName + " in genesis");
		}
		return rris.get(0);
	}
}
