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

package com.radixdlt.api.handler;

import org.json.JSONObject;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.radixdlt.api.data.BalanceEntry;
import com.radixdlt.api.data.TxHistoryEntry;
import com.radixdlt.api.service.AccountService;
import com.radixdlt.api.store.TokenBalance;
import com.radixdlt.identifiers.AccountAddress;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.identifiers.ValidatorAddress;
import com.radixdlt.utils.functional.Result;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static com.radixdlt.api.JsonRpcUtil.ARRAY;
import static com.radixdlt.api.JsonRpcUtil.fromList;
import static com.radixdlt.api.JsonRpcUtil.jsonObject;
import static com.radixdlt.api.JsonRpcUtil.safeInteger;
import static com.radixdlt.api.JsonRpcUtil.safeString;
import static com.radixdlt.api.JsonRpcUtil.withRequiredParameters;
import static com.radixdlt.api.JsonRpcUtil.withRequiredStringParameter;
import static com.radixdlt.api.data.ApiErrors.INVALID_PAGE_SIZE;
import static com.radixdlt.utils.functional.Optionals.allOf;
import static com.radixdlt.utils.functional.Result.allOf;
import static com.radixdlt.utils.functional.Result.ok;
import static com.radixdlt.utils.functional.Tuple.tuple;

@Singleton
public class ArchiveAccountHandler {
	private final AccountService accountService;

	@Inject
	public ArchiveAccountHandler(AccountService accountService) {
		this.accountService = accountService;
	}

	public JSONObject handleAccountGetBalances(JSONObject request) {
		return withRequiredStringParameter(
			request,
			"address",
			(address) -> AccountAddress.parseFunctional(address)
				.flatMap(key -> accountService.getTokenBalances(key).map(v -> tuple(key, v)))
				.map(tuple -> tuple.map(ArchiveAccountHandler::formatTokenBalances))
		);
	}

	public JSONObject handleAccountGetTransactionHistory(JSONObject request) {
		return withRequiredParameters(
			request,
			List.of("address", "size"),
			List.of("cursor"),
			params -> allOf(parseAddress(params), parseSize(params), ok(parseInstantCursor(params)))
				.flatMap(accountService::getTransactionHistory)
				.map(tuple -> tuple.map(ArchiveAccountHandler::formatHistoryResponse))
		);
	}

	public JSONObject handleAccountGetStakePositions(JSONObject request) {
		return withRequiredStringParameter(
			request,
			"address",
			(address) -> AccountAddress.parseFunctional(address)
				.flatMap(accountService::getStakePositions)
				.map(this::formatStakePositions)
		);
	}

	public JSONObject handleAccountGetUnstakePositions(JSONObject request) {
		return withRequiredStringParameter(
			request,
			"address",
			(address) -> AccountAddress.parseFunctional(address)
				.flatMap(accountService::getUnstakePositions)
				.map(positions -> {
					var curEpoch = accountService.getEpoch();
					return formatUnstakePositions(positions, curEpoch);
				})
		);
	}

	//-----------------------------------------------------------------------------------------------------
	// internal processing
	//-----------------------------------------------------------------------------------------------------

	private static JSONObject formatUnstakePositions(List<BalanceEntry> balances, long curEpoch) {
		var array = fromList(balances, unstake ->
			jsonObject()
				.put("validator", ValidatorAddress.of(unstake.getDelegate()))
				.put("amount", unstake.getAmount())
				.put("epochsUntil", unstake.getEpochUnlocked() - curEpoch)
				.put("withdrawTxID", unstake.getTxId())
		);
		return jsonObject().put(ARRAY, array);
	}

	private static JSONObject formatTokenBalances(REAddr address, List<TokenBalance> balances) {
		return jsonObject()
			.put("owner", AccountAddress.of(address))
			.put("tokenBalances", fromList(balances, TokenBalance::asJson));
	}

	private JSONObject formatStakePositions(List<BalanceEntry> balances) {
		var array = fromList(balances, balance ->
			jsonObject()
				.put("validator", ValidatorAddress.of(balance.getDelegate()))
				.put("amount", balance.getAmount())
		);

		return jsonObject().put(ARRAY, array);
	}

	private static JSONObject formatHistoryResponse(Optional<Instant> cursor, List<TxHistoryEntry> transactions) {
		return jsonObject()
			.put("cursor", cursor.map(ArchiveAccountHandler::asCursor).orElse(""))
			.put("transactions", fromList(transactions, TxHistoryEntry::asJson));
	}

	private static String asCursor(Instant instant) {
		return "" + instant.getEpochSecond() + ":" + instant.getNano();
	}

	private static Optional<Instant> parseInstantCursor(JSONObject params) {
		return safeString(params, "cursor")
			.toOptional()
			.flatMap(source -> Optional.of(source.split(":"))
				.filter(v -> v.length == 2)
				.flatMap(ArchiveAccountHandler::parseInstant));
	}

	private static Optional<Instant> parseInstant(String[] pair) {
		return allOf(parseLong(pair[0]).filter(v -> v > 0), parseInt(pair[1]).filter(v -> v >= 0))
			.map(Instant::ofEpochSecond);
	}

	private static Optional<Long> parseLong(String input) {
		try {
			return Optional.of(Long.parseLong(input));
		} catch (NumberFormatException e) {
			return Optional.empty();
		}
	}

	private static Optional<Integer> parseInt(String input) {
		try {
			return Optional.of(Integer.parseInt(input));
		} catch (NumberFormatException e) {
			return Optional.empty();
		}
	}

	private static Result<Integer> parseSize(JSONObject params) {
		return safeInteger(params, "size")
			.filter(value -> value > 0, INVALID_PAGE_SIZE);
	}

	private static Result<REAddr> parseAddress(JSONObject params) {
		return AccountAddress.parseFunctional(params.getString("address"));
	}
}
