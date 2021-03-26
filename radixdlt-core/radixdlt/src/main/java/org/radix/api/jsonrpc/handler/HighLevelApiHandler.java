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
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.HashUtils;
import com.radixdlt.identifiers.AID;
import com.radixdlt.identifiers.RRI;
import com.radixdlt.identifiers.RadixAddress;
import com.radixdlt.utils.UInt256;
import com.radixdlt.utils.functional.Failure;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.radix.api.jsonrpc.JsonRpcUtil.errorResponse;
import static org.radix.api.jsonrpc.JsonRpcUtil.fromList;
import static org.radix.api.jsonrpc.JsonRpcUtil.jsonArray;
import static org.radix.api.jsonrpc.JsonRpcUtil.jsonObject;
import static org.radix.api.jsonrpc.JsonRpcUtil.response;
import static org.radix.api.jsonrpc.JsonRpcUtil.safeInteger;
import static org.radix.api.jsonrpc.JsonRpcUtil.withRequiredParameter;
import static org.radix.api.jsonrpc.JsonRpcUtil.withRequiredParameters;
import static org.radix.api.services.ApiAtomStatus.FAILED;
import static org.radix.api.services.ApiAtomStatus.PENDING;
import static org.radix.api.services.ApiAtomStatus.fromAtomStatus;

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
		return withRequiredParameters(request, Set.of("address", "size"), params -> {
			var address = RadixAddress.fromString(params.getString("address"));
			var size = safeInteger(params, "size").filter(value -> value > 0);

			if (address.isEmpty() || size.isEmpty()) {
				return errorResponse(request, RpcError.INVALID_PARAMS,
									 address.isEmpty() ? "Unable to recognize address" : "Invalid size"
				);
			}

			var transactions = jsonArray();
			AID newCursor = null;

			for (int i = 0; i < size.get(); i++) {
				var transaction = SingleTransaction.generate();
				newCursor = transaction.getAid();
				transactions.put(transaction.asJsonObj());
			}

			return response(request, jsonObject().put("cursor", newCursor).put("transactions", transactions));
		});
	}

	public JSONObject handleTransactionStatus(JSONObject request) {
		return withRequiredParameter(request, "atomIdentifier", (params, atomId) ->
			AID.fromString(atomId)
				.map(aid -> response(request, stubTransactionStatus(aid)))
				.orElseGet(() -> errorResponse(request, RpcError.INVALID_PARAMS, "Unable to recognize atom ID")));
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

	public static class SingleTransaction {
		private final AID atomId;
		private final Instant sentAt;
		private final UInt256 fee;
		private final List<Map<String, Object>> actions;

		public SingleTransaction(
			AID atomId,
			Instant sentAt,
			UInt256 fee,
			List<Map<String, Object>> actions
		) {
			this.atomId = atomId;
			this.sentAt = sentAt;
			this.fee = fee;
			this.actions = actions;
		}

		public static SingleTransaction generate() {
			var atomId = AID.from(HashUtils.random(AID.BYTES).asBytes());
			var sentAt = Instant.now().minus(random.nextInt(60 * 24) + 1L, ChronoUnit.MINUTES);
			var fee = UInt256.from(random.nextInt(1000));
			var actions = IntStream.range(0, random.nextInt(5) + 1)
				.mapToObj(n -> randomAction()).collect(Collectors.toList());

			return new SingleTransaction(atomId, sentAt, fee, actions);
		}

		private static Map<String, Object> randomAction() {
			var types = new String[]{"UNKNOWN", "stake", "unstake", "tokenTransfer"};
			var type = random.nextInt(types.length);
			var from = new RadixAddress((byte) 0, ECKeyPair.generateNew().getPublicKey());
			var to = new RadixAddress((byte) 0, ECKeyPair.generateNew().getPublicKey());
			var amount = UInt256.from(random.nextInt(1000));

			switch (type) {
				case 0: // Unknown
					return Map.of("type", types[type], "particleGroup", "Unable to decode");
				case 1:    // Stake
					return Map.of("type", types[type], "to", to, "amount", amount);
				case 2: // Unstake
					return Map.of("type", types[type], "from", from, "amount", amount);
				case 3: // transfer
					return Map.of("type", types[type], "from", from, "to", to, "amount", amount);
			}

			throw new IllegalStateException("Should not happen! " + type);
		}

		public AID getAid() {
			return atomId;
		}

		@Override
		public String toString() {
			return asJsonObj().toString();
		}

		public JSONObject asJsonObj() {
			var list = jsonArray();

			actions.forEach(action -> {
				var obj = jsonObject();

				action.forEach((k, v) -> obj.put(k, v.toString()));

				list.put(obj);
			});

			return jsonObject()
				.put("atomId", atomId.toString())
				.put("sentAt", DateTimeFormatter.ISO_INSTANT.format(sentAt))
				.put("fee", fee.toString())
				.put("actions", list);
		}
	}
}
