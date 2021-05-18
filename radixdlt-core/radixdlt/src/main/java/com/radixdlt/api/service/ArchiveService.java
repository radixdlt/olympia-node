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

package com.radixdlt.api.service;

import com.google.inject.Inject;
import com.radixdlt.api.Rri;
import com.radixdlt.api.data.TxHistoryEntry;
import com.radixdlt.api.store.ClientApiStore;
import com.radixdlt.api.store.TokenBalance;
import com.radixdlt.api.store.TokenDefinitionRecord;
import com.radixdlt.api.data.BalanceEntry;
import com.radixdlt.api.data.UnstakeEntry;
import com.radixdlt.identifiers.AID;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.utils.functional.Result;
import com.radixdlt.utils.functional.Tuple.Tuple2;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.radixdlt.utils.functional.Tuple.tuple;

public class ArchiveService {
	private final ClientApiStore clientApiStore;

	@Inject
	public ArchiveService(ClientApiStore clientApiStore) {
		this.clientApiStore = clientApiStore;
	}

	public long getEpoch() {
		return clientApiStore.getEpoch();
	}

	public Result<List<TokenBalance>> getTokenBalances(REAddr addr) {
		return clientApiStore.getTokenBalances(addr, ClientApiStore.BalanceType.SPENDABLE)
			.map(list -> list.stream().map(TokenBalance::from).collect(Collectors.toList()));
	}

	public Result<TokenDefinitionRecord> getNativeTokenDescription() {
		return clientApiStore.getTokenDefinition(REAddr.ofNativeToken())
			.flatMap(r -> clientApiStore.getTokenSupply(Rri.of(r.getSymbol(), REAddr.ofNativeToken())).map(r::withSupply));
	}

	public Result<TokenDefinitionRecord> getTokenDescription(String rri) {
		return clientApiStore.parseRri(rri)
			.flatMap(clientApiStore::getTokenDefinition)
			.flatMap(definition -> withSupply(rri, definition));
	}

	public Result<Tuple2<Optional<Instant>, List<TxHistoryEntry>>> getTransactionHistory(
		REAddr address, int size, Optional<Instant> cursor
	) {
		return clientApiStore.getTransactionHistory(address, size, cursor)
			.map(response -> tuple(calculateNewCursor(response), response));
	}

	public Result<TxHistoryEntry> getTransaction(AID txId) {
		return clientApiStore.getTransaction(txId);
	}

	public Result<List<BalanceEntry>> getStakePositions(REAddr addr) {
		return clientApiStore.getTokenBalances(addr, ClientApiStore.BalanceType.STAKES);
	}

	// Everything is immediately spendable in betanet
	public Result<List<BalanceEntry>> getUnstakePositions(REAddr addr) {
		return clientApiStore.getTokenBalances(addr, ClientApiStore.BalanceType.UNSTAKES);
	}

	private static Optional<Instant> calculateNewCursor(List<TxHistoryEntry> response) {
		return response.stream()
			.reduce(ArchiveService::findLast)
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
