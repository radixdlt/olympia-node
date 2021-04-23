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
import com.radixdlt.client.Rri;
import com.radixdlt.client.api.TransactionAction;
import com.radixdlt.client.handler.ActionParser;
import com.radixdlt.client.store.ClientApiStore;
import com.radixdlt.client.store.TokenBalance;
import com.radixdlt.client.store.TokenDefinitionRecord;
import com.radixdlt.client.store.berkeley.BalanceEntry;
import com.radixdlt.client.store.berkeley.UnstakeEntry;
import com.radixdlt.identifiers.AID;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.identifiers.RadixAddress;
import com.radixdlt.store.ImmutableIndex;
import com.radixdlt.client.api.TxHistoryEntry;

import com.radixdlt.universe.Universe;
import com.radixdlt.utils.functional.Result;
import com.radixdlt.utils.functional.Tuple.Tuple2;
import org.json.JSONArray;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.radixdlt.utils.functional.Tuple.tuple;

public class HighLevelApiService {

	private final Universe universe;
	private final ClientApiStore clientApiStore;
	private final ImmutableIndex immutableIndex;

	@Inject
	public HighLevelApiService(
		Universe universe,
		ClientApiStore clientApiStore,
		ImmutableIndex immutableIndex
	) {
		this.universe = universe;
		this.clientApiStore = clientApiStore;
		this.immutableIndex = immutableIndex;
	}

	public int getUniverseMagic() {
		return universe.getMagic();
	}

	public Result<List<TokenBalance>> getTokenBalances(RadixAddress radixAddress) {
		return clientApiStore.getTokenBalances(radixAddress, false)
			.map(list -> list.stream().map(TokenBalance::from).collect(Collectors.toList()));
	}

	public Result<TokenDefinitionRecord> getNativeTokenDescription() {
		return clientApiStore.getTokenDefinition(REAddr.ofNativeToken())
			.flatMap(r -> clientApiStore.getTokenSupply(Rri.of(r.getSymbol(), REAddr.ofNativeToken())).map(r::withSupply));
	}

	public Result<List<TransactionAction>> parse(JSONArray actions) {
		return ActionParser.parse(actions, clientApiStore);
	}

	public Result<TokenDefinitionRecord> getTokenDescription(String rri) {
		return clientApiStore.parseRri(rri)
			.flatMap(clientApiStore::getTokenDefinition)
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

	public Result<List<BalanceEntry>> getStakePositions(RadixAddress radixAddress) {
		return clientApiStore.getTokenBalances(radixAddress, true);
	}

	public Result<List<UnstakeEntry>> getUnstakePositions(RadixAddress radixAddress) {
		return clientApiStore.getUnstakePositions(radixAddress);
	}

	private static Optional<Instant> calculateNewCursor(List<TxHistoryEntry> response) {
		return response.stream()
			.reduce(HighLevelApiService::findLast)
			.map(TxHistoryEntry::timestamp);
	}

	private static <T> T findLast(T first, T second) {
		return second;
	}

	private Result<TokenDefinitionRecord> withSupply(String rri, TokenDefinitionRecord definition) {
		return definition.isMutable()
			   ? clientApiStore.getTokenSupply(rri).map(definition::withSupply)
			   : Result.ok(definition);
	}
}
