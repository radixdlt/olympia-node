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
import com.radixdlt.atom.SubstateSerializer;
import com.radixdlt.atom.Txn;
import com.radixdlt.atommodel.tokens.MutableSupplyTokenDefinitionParticle;
import com.radixdlt.atommodel.tokens.TokenDefinitionUtils;
import com.radixdlt.client.store.ClientApiStore;
import com.radixdlt.client.store.TokenBalance;
import com.radixdlt.client.store.TokenDefinitionRecord;
import com.radixdlt.constraintmachine.ConstraintMachine;
import com.radixdlt.client.store.TxHistoryEntry;
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.constraintmachine.REInstruction;
import com.radixdlt.identifiers.RRI;
import com.radixdlt.identifiers.RadixAddress;
import com.radixdlt.serialization.DeserializeException;
import com.radixdlt.statecomputer.checkpoint.Genesis;
import com.radixdlt.universe.Universe;
import com.radixdlt.utils.functional.Result;
import com.radixdlt.utils.functional.Tuple;
import com.radixdlt.utils.functional.Tuple.Tuple2;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static com.radixdlt.utils.functional.Tuple.tuple;

public class HighLevelApiService {
	private final Universe universe;
	private final ClientApiStore clientApiStore;
	private final MutableSupplyTokenDefinitionParticle nativeTokenDefinition;

	@Inject
	public HighLevelApiService(
		Universe universe,
		ClientApiStore clientApiStore,
		@Genesis List<Txn> genesisAtoms
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

	public Result<Tuple2<Optional<Instant>, List<TxHistoryEntry>>> getTransactionHistory(
		RadixAddress address, int size, Optional<Instant> cursor
	) {
		return clientApiStore.getTransactionHistory(address, size, cursor)
			.map(response -> tuple(calculateNewCursor(response), response));

	}

	private Optional<Instant> calculateNewCursor(List<TxHistoryEntry> response) {
		return response.stream()
			.reduce((first, second) -> second)
			.map(TxHistoryEntry::timestamp);
	}

	private Result<TokenDefinitionRecord> withSupply(RRI rri, TokenDefinitionRecord definition) {
		return definition.isMutable()
			   ? clientApiStore.getTokenSupply(rri).map(definition::withSupply)
			   : Result.ok(definition);
	}

	private static MutableSupplyTokenDefinitionParticle nativeToken(List<Txn> genesisAtoms) {
		return genesisAtoms.stream()
			.map(txn -> {
				try {
					return DefaultSerialization.getInstance().fromDson(txn.getPayload(), Atom.class);
				} catch (DeserializeException e) {
					throw new IllegalStateException();
				}
			})
			.flatMap(a -> ConstraintMachine.toInstructions(a.getInstructions()).stream())
			.filter(i -> i.getMicroOp() == REInstruction.REOp.UP)
			.map(i -> {
				try {
					return SubstateSerializer.deserialize(i.getData());
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
