/* Copyright 2021 Radix Publishing Ltd incorporated in Jersey (Channel Islands).
 *
 * Licensed under the Radix License, Version 1.0 (the "License"); you may not use this
 * file except in compliance with the License. You may obtain a copy of the License at:
 *
 * radixfoundation.org/licenses/LICENSE-v1
 *
 * The Licensor hereby grants permission for the Canonical version of the Work to be
 * published, distributed and used under or by reference to the Licensor’s trademark
 * Radix ® and use of any unregistered trade names, logos or get-up.
 *
 * The Licensor provides the Work (and each Contributor provides its Contributions) on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied,
 * including, without limitation, any warranties or conditions of TITLE, NON-INFRINGEMENT,
 * MERCHANTABILITY, or FITNESS FOR A PARTICULAR PURPOSE.
 *
 * Whilst the Work is capable of being deployed, used and adopted (instantiated) to create
 * a distributed ledger it is your responsibility to test and validate the code, together
 * with all logic and performance of that code under all foreseeable scenarios.
 *
 * The Licensor does not make or purport to make and hereby excludes liability for all
 * and any representation, warranty or undertaking in any form whatsoever, whether express
 * or implied, to any entity or person, including any representation, warranty or
 * undertaking, as to the functionality security use, value or other characteristics of
 * any distributed ledger nor in respect the functioning or value of any tokens which may
 * be created stored or transferred using the Work. The Licensor does not warrant that the
 * Work or any use of the Work complies with any law or regulation in any territory where
 * it may be implemented or used or that it will be appropriate for any specific purpose.
 *
 * Neither the licensor nor any current or former employees, officers, directors, partners,
 * trustees, representatives, agents, advisors, contractors, or volunteers of the Licensor
 * shall be liable for any direct or indirect, special, incidental, consequential or other
 * losses of any kind, in tort, contract or otherwise (including but not limited to loss
 * of revenue, income or profits, or loss of use or data, or loss of reputation, or loss
 * of any economic or other opportunity of whatsoever nature or howsoever arising), arising
 * out of or in connection with (without limitation of any use, misuse, of any ledger system
 * or use made or its functionality or any performance or operation of any code or protocol
 * caused by bugs or programming or logic errors or otherwise);
 *
 * A. any offer, purchase, holding, use, sale, exchange or transmission of any
 * cryptographic keys, tokens or assets created, exchanged, stored or arising from any
 * interaction with the Work;
 *
 * B. any failure in a transmission or loss of any token or assets keys or other digital
 * artefacts due to errors in transmission;
 *
 * C. bugs, hacks, logic errors or faults in the Work or any communication;
 *
 * D. system software or apparatus including but not limited to losses caused by errors
 * in holding or transmitting tokens by any third-party;
 *
 * E. breaches or failure of security including hacker attacks, loss or disclosure of
 * password, loss of private key, unauthorised use or misuse of such passwords or keys;
 *
 * F. any losses including loss of anticipated savings or other benefits resulting from
 * use of the Work or any changes to the Work (however implemented).
 *
 * You are solely responsible for; testing, validating and evaluation of all operation
 * logic, functionality, security and appropriateness of using the Work for any commercial
 * or non-commercial purpose and for any reproduction or redistribution by You of the
 * Work. You assume all risks associated with Your use of the Work and the exercise of
 * permissions under this License.
 */

package com.radixdlt.api.handler;

import org.json.JSONObject;

import com.google.inject.Inject;
import com.radixdlt.api.data.BalanceEntry;
import com.radixdlt.api.data.TxHistoryEntry;
import com.radixdlt.api.service.ArchiveAccountService;
import com.radixdlt.api.store.TokenBalance;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.networks.Addressing;
import com.radixdlt.utils.functional.Result;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static com.radixdlt.api.JsonRpcUtil.fromCollection;
import static com.radixdlt.api.JsonRpcUtil.jsonObject;
import static com.radixdlt.api.JsonRpcUtil.safeInteger;
import static com.radixdlt.api.JsonRpcUtil.safeString;
import static com.radixdlt.api.JsonRpcUtil.withRequiredParameters;
import static com.radixdlt.api.JsonRpcUtil.withRequiredStringParameter;
import static com.radixdlt.api.JsonRpcUtil.wrapArray;
import static com.radixdlt.errors.ParameterError.INVALID_PAGE_SIZE;
import static com.radixdlt.utils.functional.Optionals.allOf;
import static com.radixdlt.utils.functional.Result.allOf;
import static com.radixdlt.utils.functional.Result.ok;
import static com.radixdlt.utils.functional.Tuple.tuple;

public class ArchiveAccountHandler {
	private final ArchiveAccountService accountService;
	private final Addressing addressing;

	@Inject
	public ArchiveAccountHandler(
		ArchiveAccountService accountService,
		Addressing addressing
	) {
		this.accountService = accountService;
		this.addressing = addressing;
	}

	public JSONObject handleAccountGetBalances(JSONObject request) {
		return withRequiredStringParameter(
			request,
			"address",
			address -> addressing.forAccounts().parseFunctional(address)
				.flatMap(key -> accountService.getTokenBalances(key).map(v -> tuple(key, v)))
				.map(tuple -> tuple.map(this::formatTokenBalances))
		);
	}

	public JSONObject handleAccountGetTransactionHistory(JSONObject request) {
		return withRequiredParameters(
			request,
			List.of("address", "size"),
			List.of("cursor", "verbose"),
			params -> allOf(parseAddress(params), parseSize(params), ok(parseInstantCursor(params)), parseVerboseFlag(params))
				.flatMap(accountService::getTransactionHistory)
				.map(tuple -> tuple.map(ArchiveAccountHandler::formatHistoryResponse))
		);
	}

	public JSONObject handleAccountGetStakePositions(JSONObject request) {
		return withRequiredStringParameter(
			request,
			"address",
			address -> addressing.forAccounts().parseFunctional(address)
				.flatMap(accountService::getStakePositions)
				.map(this::formatStakePositions)
		);
	}

	public JSONObject handleAccountGetUnstakePositions(JSONObject request) {
		return withRequiredStringParameter(
			request,
			"address",
			address -> addressing.forAccounts().parseFunctional(address)
				.flatMap(accountService::getUnstakePositions)
				.map(positions -> formatUnstakePositions(positions, accountService.getEpoch()))
		);
	}

	//-----------------------------------------------------------------------------------------------------
	// internal processing
	//-----------------------------------------------------------------------------------------------------

	private JSONObject formatUnstakePositions(List<BalanceEntry> balances, long curEpoch) {
		return wrapArray(fromCollection(balances, unstake ->
			jsonObject()
				.put("validator", addressing.forValidators().of(unstake.getDelegate()))
				.put("amount", unstake.getAmount())
				.put("epochsUntil", unstake.getEpochUnlocked() - curEpoch)
				.put("withdrawTxID", unstake.getTxId())
		));
	}

	private JSONObject formatTokenBalances(REAddr address, List<TokenBalance> balances) {
		return jsonObject()
			.put("owner", addressing.forAccounts().of(address))
			.put("tokenBalances", fromCollection(balances, TokenBalance::asJson));
	}

	private JSONObject formatStakePositions(List<BalanceEntry> balances) {
		return wrapArray(fromCollection(balances, balance ->
			jsonObject()
				.put("validator", addressing.forValidators().of(balance.getDelegate()))
				.put("amount", balance.getAmount())
		));
	}

	private static JSONObject formatHistoryResponse(Optional<Instant> cursor, List<TxHistoryEntry> transactions) {
		return jsonObject()
			.put("cursor", cursor.map(ArchiveAccountHandler::asCursor).orElse(""))
			.put("transactions", fromCollection(transactions, TxHistoryEntry::asJson));
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

	private Result<REAddr> parseAddress(JSONObject params) {
		return addressing.forAccounts().parseFunctional(params.getString("address"));
	}

	private Result<Boolean> parseVerboseFlag(JSONObject params) {
		return ok(params.optBoolean("verbose", false));
	}
}
