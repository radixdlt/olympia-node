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
import com.radixdlt.api.data.BalanceEntry;
import com.radixdlt.api.data.TxHistoryEntry;
import com.radixdlt.api.data.UnstakeEntry;
import com.radixdlt.api.store.ClientApiStore;
import com.radixdlt.api.store.TokenBalance;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.utils.functional.Result;
import com.radixdlt.utils.functional.Tuple.Tuple2;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.radixdlt.utils.functional.Tuple.tuple;

public class AccountService {
	private final ClientApiStore clientApiStore;

	@Inject
	public AccountService(ClientApiStore clientApiStore) {
		this.clientApiStore = clientApiStore;
	}

	public Result<List<TokenBalance>> getTokenBalances(REAddr addr) {
		return clientApiStore.getTokenBalances(addr, false)
			.map(list -> list.stream().map(TokenBalance::from).collect(Collectors.toList()));
	}

	public Result<Tuple2<Optional<Instant>, List<TxHistoryEntry>>> getTransactionHistory(
		REAddr address, int size, Optional<Instant> cursor
	) {
		return clientApiStore.getTransactionHistory(address, size, cursor)
			.map(response -> tuple(calculateNewCursor(response), response));
	}

	public Result<List<BalanceEntry>> getStakePositions(REAddr addr) {
		return clientApiStore.getTokenBalances(addr, true);
	}

	//TODO: restore functionality, not everything is spendable anymore
	public Result<List<UnstakeEntry>> getUnstakePositions(REAddr addr) {
		return Result.ok(List.of());
	}

	private static Optional<Instant> calculateNewCursor(List<TxHistoryEntry> response) {
		return response.stream()
			.reduce(AccountService::findLast)
			.map(TxHistoryEntry::timestamp);
	}

	private static <T> T findLast(T first, T second) {
		return second;
	}
}
