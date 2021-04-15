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

package com.radixdlt.client.service;

import com.google.inject.Inject;
import com.radixdlt.DefaultSerialization;
import com.radixdlt.atom.Atom;
import com.radixdlt.atom.SubstateSerializer;
import com.radixdlt.atom.Txn;
import com.radixdlt.atommodel.tokens.TokenDefinitionParticle;
import com.radixdlt.atommodel.tokens.TokenDefinitionUtils;
import com.radixdlt.client.ClientUtils;
import com.radixdlt.client.store.ClientApiStore;
import com.radixdlt.client.store.TokenBalance;
import com.radixdlt.client.store.TokenDefinitionRecord;
import com.radixdlt.client.api.TxHistoryEntry;
import com.radixdlt.constraintmachine.REInstruction;
import com.radixdlt.identifiers.AID;
import com.radixdlt.identifiers.RRI;
import com.radixdlt.identifiers.RadixAddress;
import com.radixdlt.serialization.DeserializeException;
import com.radixdlt.statecomputer.checkpoint.Genesis;
import com.radixdlt.universe.Universe;
import com.radixdlt.utils.functional.Result;
import com.radixdlt.utils.functional.Tuple.Tuple2;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static com.radixdlt.utils.functional.Tuple.tuple;

public class HighLevelApiService {
	private final Universe universe;
	private final ClientApiStore clientApiStore;
	private final TokenDefinitionParticle nativeTokenDefinition;

	@Inject
	public HighLevelApiService(
		Universe universe,
		ClientApiStore clientApiStore,
		@Genesis List<Txn> genesisTxns
	) {
		this.universe = universe;
		this.clientApiStore = clientApiStore;
		this.nativeTokenDefinition = ClientUtils.nativeToken(genesisTxns);
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

	public Result<TxHistoryEntry> getTransaction(AID txId) {
		return clientApiStore.getTransaction(txId);
	}

	private static Optional<Instant> calculateNewCursor(List<TxHistoryEntry> response) {
		return response.stream()
			.reduce(HighLevelApiService::findLast)
			.map(TxHistoryEntry::timestamp);
	}

	private static <T> T findLast(T first, T second) {
		return second;
	}

	private Result<TokenDefinitionRecord> withSupply(RRI rri, TokenDefinitionRecord definition) {
		return definition.isMutable()
			   ? clientApiStore.getTokenSupply(rri).map(definition::withSupply)
			   : Result.ok(definition);
	}
}
