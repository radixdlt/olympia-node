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

package org.radix.api.jsonrpc.handler;

import org.json.JSONObject;
import org.radix.api.jsonrpc.AtomStatus;
import org.radix.api.jsonrpc.JsonRpcUtil;
import org.radix.api.jsonrpc.JsonRpcUtil.RpcError;
import org.radix.api.services.HighLevelApiService;

import com.google.inject.Inject;
import com.radixdlt.client.store.TokenBalance;
import com.radixdlt.client.store.TxHistoryEntry;
import com.radixdlt.identifiers.AID;
import com.radixdlt.identifiers.RRI;
import com.radixdlt.identifiers.RadixAddress;
import com.radixdlt.utils.functional.Failure;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static org.radix.api.jsonrpc.JsonRpcUtil.errorResponse;
import static org.radix.api.jsonrpc.JsonRpcUtil.fromList;
import static org.radix.api.jsonrpc.JsonRpcUtil.jsonObject;
import static org.radix.api.jsonrpc.JsonRpcUtil.response;
import static org.radix.api.jsonrpc.JsonRpcUtil.safeInteger;
import static org.radix.api.jsonrpc.JsonRpcUtil.withRequiredParameter;
import static org.radix.api.jsonrpc.JsonRpcUtil.withRequiredParameters;
import static org.radix.api.services.ApiAtomStatus.FAILED;
import static org.radix.api.services.ApiAtomStatus.PENDING;
import static org.radix.api.services.ApiAtomStatus.fromAtomStatus;

import static com.radixdlt.utils.functional.Optionals.allOf;

public class HighLevelApiHandler {
	private final HighLevelApiService highLevelApiService;

	@Inject
	public HighLevelApiHandler(HighLevelApiService highLevelApiService) {
		this.highLevelApiService = highLevelApiService;
	}

	public JSONObject handleUniverseMagic(JSONObject request) {
		return response(request, jsonObject().put("networkId", highLevelApiService.getUniverseMagic()));
	}

	public JSONObject handleNativeToken(JSONObject request) {
		return highLevelApiService.getNativeTokenDescription()
			.fold(
				failure -> toErrorResponse(request, failure),
				description -> response(request, description.asJson())
			);
	}

	public JSONObject handleTokenInfo(JSONObject request) {
		return withRequiredParameter(
			request, "resourceIdentifier",
			(params, tokenId) -> RRI.fromString(tokenId)
				.flatMap(highLevelApiService::getTokenDescription)
				.fold(
					failure -> toErrorResponse(request, failure),
					description -> response(request, description.asJson())
				)
		);
	}

	public JSONObject handleTokenBalances(JSONObject request) {
		return withRequiredParameter(
			request, "address",
			(params, address) -> RadixAddress.fromString(address)
				.map(radixAddress -> response(request, formatTokenBalances(request, radixAddress)))
				.orElseGet(() -> errorResponse(request, RpcError.INVALID_PARAMS, "Unable to recognize address"))
		);
	}

	public JSONObject handleTransactionHistory(JSONObject request) {
		return withRequiredParameters(request, Set.of("address", "size"), __ -> respondWithTransactionHistory(request));
	}

	public JSONObject handleTransactionStatus(JSONObject request) {
		return withRequiredParameter(request, "txID", (params, atomId) ->
			AID.fromString(atomId)
				.map(aid -> response(request, stubTransactionStatus(aid)))
				.orElseGet(() -> errorResponse(request, RpcError.INVALID_PARAMS, "Unable to recognize transaction ID")));
	}

	private JSONObject respondWithTransactionHistory(JSONObject request) {
		return allOf(Optional.of(request), parseAddress(request), parseSize(request))
			.map(this::formatTransactionHistory)
			.orElseGet(() -> errorResponse(request, RpcError.INVALID_PARAMS, "One or more required parameters missing"));
	}

	private JSONObject formatTransactionHistory(JSONObject request, RadixAddress address, int size) {
		return highLevelApiService
			.getTransactionHistory(address, size, parseCursor(request))
			.fold(
				failure -> errorResponse(request, RpcError.SERVER_ERROR, failure.message()),
				value -> value.map((newCursor, transactions) -> buildTransactionHistoryResponse(request, newCursor, transactions))
			);
	}

	private static JSONObject buildTransactionHistoryResponse(
		JSONObject request, Optional<Instant> cursor, List<TxHistoryEntry> transactions
	) {
		return response(
			request,
			jsonObject()
				.put("cursor", cursor.map(HighLevelApiHandler::asCursor).orElse(""))
				.put("transactions", fromList(transactions, TxHistoryEntry::asJson))
		);
	}

	private static String asCursor(Instant instant) {
		return "" + instant.getEpochSecond() + ":" + instant.getNano();
	}

	private static Optional<Instant> parseCursor(JSONObject request) {
		var params = JsonRpcUtil.params(request);

		return !params.has("cursor")
			   ? Optional.empty()
			   : Optional.of(params.getString("cursor")).flatMap(HighLevelApiHandler::instantFromString);
	}

	private static Optional<Instant> instantFromString(String source) {
		return Optional.of(source.split(":"))
			.filter(v -> v.length == 2)
			.flatMap(HighLevelApiHandler::parseInstant);
	}

	private static Optional<Instant> parseInstant(String[] pair) {
		return allOf(parseLong(pair[0]).filter(v -> v > 0), parseInt(pair[1]).filter(v -> v > 0))
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

	private static Optional<Integer> parseSize(JSONObject request) {
		return safeInteger(JsonRpcUtil.params(request), "size")
			.filter(value -> value > 0);
	}

	private static Optional<RadixAddress> parseAddress(JSONObject request) {
		return RadixAddress.fromString(JsonRpcUtil.params(request).getString("address"));
	}

	private JSONObject formatTokenBalances(JSONObject request, RadixAddress radixAddress) {
		return highLevelApiService.getTokenBalances(radixAddress)
			.fold(
				failure -> toErrorResponse(request, failure),
				list -> jsonObject()
					.put("owner", radixAddress.toString())
					.put("tokenBalances", fromList(list, TokenBalance::asJson))
			);
	}

	private JSONObject toErrorResponse(JSONObject request, Failure failure) {
		return errorResponse(request, RpcError.INVALID_PARAMS, failure.message());
	}

	//TODO: remove all code below once functionality will be implemented
	//---------------------------------------------------------------------
	// Stubs
	//---------------------------------------------------------------------

	private final ConcurrentMap<AID, AtomStatus> atomStatuses = new ConcurrentHashMap<>();

	private static final SecureRandom random = new SecureRandom();
	private static final AtomStatus[] STATUSES = AtomStatus.values();
	private static final int LIMIT = STATUSES.length;

	private JSONObject stubTransactionStatus(AID aid) {
		var originalStatus = atomStatuses.computeIfAbsent(aid, key -> STATUSES[random.nextInt(LIMIT)]);
		var status = fromAtomStatus(originalStatus);

		var result = jsonObject().put("atomIdentifier", aid.toString()).put("status", status);

		if (status == FAILED) {
			result.put("failure", originalStatus.toString());
		}

		if (status == PENDING) {
			//Prepare for the next request
			atomStatuses.put(aid, random.nextBoolean() ? AtomStatus.STORED : AtomStatus.EVICTED_FAILED_CM_VERIFICATION);
		}

		return result;
	}
}
