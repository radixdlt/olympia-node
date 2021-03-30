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

package org.radix.api.services;

import com.google.inject.Inject;
import com.radixdlt.DefaultSerialization;
import com.radixdlt.atom.Atom;
import com.radixdlt.atommodel.tokens.MutableSupplyTokenDefinitionParticle;
import com.radixdlt.atommodel.tokens.TokenDefinitionUtils;
import com.radixdlt.client.store.ClientApiStore;
import com.radixdlt.client.store.TokenBalance;
import com.radixdlt.client.store.TokenDefinitionRecord;
import com.radixdlt.consensus.bft.Self;
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.constraintmachine.Spin;
import com.radixdlt.identifiers.RRI;
import com.radixdlt.identifiers.RadixAddress;
import com.radixdlt.serialization.DeserializeException;
import com.radixdlt.statecomputer.checkpoint.Genesis;
import com.radixdlt.universe.Universe;
import com.radixdlt.utils.functional.Result;

import java.util.List;

public class HighLevelApiService {
	private final Universe universe;
	private final ClientApiStore clientApiStore;
	private final MutableSupplyTokenDefinitionParticle nativeTokenDefinition;

	@Inject
	public HighLevelApiService(
		Universe universe,
		ClientApiStore clientApiStore,
		@Genesis List<Atom> genesisAtoms
	) {
		this.universe = universe;
		this.clientApiStore = clientApiStore;
		this.nativeTokenDefinition = nativeToken(genesisAtoms);
	}

	public int getUniverseMagic() {
		return universe.getMagic();
	}

	public Result<List<TokenBalance>> getTokenBalances(RadixAddress radixAddress) {
		return clientApiStore.getTokenBalances(radixAddress);
	}

	public Result<TokenDefinitionRecord> getNativeTokenDescription() {
		return clientApiStore.getTokenSupply(nativeTokenDefinition.getRRI())
			.map(supply -> TokenDefinitionRecord.from(nativeTokenDefinition, supply));
	}

	public Result<TokenDefinitionRecord> getTokenDescription(RRI rri) {
		return clientApiStore.getTokenDefinition(rri)
			.flatMap(definition -> withSupply(rri, definition));
	}

	private Result<TokenDefinitionRecord> withSupply(RRI rri, TokenDefinitionRecord definition) {
		return definition.isMutable()
			   ? clientApiStore.getTokenSupply(rri).map(definition::withSupply)
			   : Result.ok(definition);
	}

	private static MutableSupplyTokenDefinitionParticle nativeToken(List<Atom> genesisAtoms) {
		return genesisAtoms.stream().flatMap(Atom::uniqueInstructions)
			.filter(i -> i.getNextSpin() == Spin.UP)
			.map(i -> {
				try {
					return DefaultSerialization.getInstance().fromDson(i.getData(), Particle.class);
				} catch (DeserializeException e) {
					throw new IllegalStateException("Cannot deserialize genesis");
				}
			})
			.filter(MutableSupplyTokenDefinitionParticle.class::isInstance)
			.map(MutableSupplyTokenDefinitionParticle.class::cast)
			.filter(particle -> particle.getRRI().getName().equals(TokenDefinitionUtils.getNativeTokenShortCode()))
			.findFirst()
			.orElseThrow(() -> new IllegalStateException("Unable to retrieve native token definition"));
	}
}
