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

package com.radixdlt.client.handler;

import org.bouncycastle.util.encoders.DecoderException;
import org.bouncycastle.util.encoders.Hex;
import org.json.JSONException;
import org.json.JSONObject;
import org.radix.api.jsonrpc.JsonRpcUtil;
import org.radix.api.jsonrpc.JsonRpcUtil.RpcError;

import com.radixdlt.client.service.HighLevelApiService;

import com.google.inject.Inject;
import com.radixdlt.client.service.SubmissionService;
import com.radixdlt.client.service.TransactionStatusService;
import com.radixdlt.client.store.TokenBalance;
import com.radixdlt.client.api.TxHistoryEntry;
import com.radixdlt.crypto.ECDSASignature;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.crypto.exception.PublicKeyException;
import com.radixdlt.identifiers.AID;
import com.radixdlt.identifiers.RRI;
import com.radixdlt.identifiers.RadixAddress;
import com.radixdlt.utils.functional.Failure;
import com.radixdlt.utils.functional.Result;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;

import static org.radix.api.jsonrpc.JsonRpcUtil.errorResponse;
import static org.radix.api.jsonrpc.JsonRpcUtil.fromList;
import static org.radix.api.jsonrpc.JsonRpcUtil.jsonObject;
import static org.radix.api.jsonrpc.JsonRpcUtil.response;
import static org.radix.api.jsonrpc.JsonRpcUtil.safeInteger;
import static org.radix.api.jsonrpc.JsonRpcUtil.safeString;
import static org.radix.api.jsonrpc.JsonRpcUtil.withRequiredArrayParameter;
import static org.radix.api.jsonrpc.JsonRpcUtil.withRequiredStringParameter;
import static org.radix.api.jsonrpc.JsonRpcUtil.withRequiredParameters;

import static com.radixdlt.utils.functional.Optionals.allOf;

public class HighLevelApiHandler {
	private final HighLevelApiService highLevelApiService;
	private final TransactionStatusService transactionStatusService;
	private final SubmissionService submissionService;

	@Inject
	public HighLevelApiHandler(
		HighLevelApiService highLevelApiService,
		TransactionStatusService transactionStatusService,
		SubmissionService submissionService
	) {
		this.highLevelApiService = highLevelApiService;
		this.transactionStatusService = transactionStatusService;
		this.submissionService = submissionService;
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
		return withRequiredStringParameter(
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
		return withRequiredStringParameter(
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
		return withRequiredStringParameter(request, "txID", (params, idString) ->
			AID.fromString(idString)
				.map(txId -> response(request, formatTransactionStatus(txId)))
				.orElseGet(() -> errorResponse(request, RpcError.INVALID_PARAMS, "Unable to recognize transaction ID")));
	}

	public JSONObject handleLookupTransaction(JSONObject request) {
		return withRequiredStringParameter(request, "txID", (params, idString) ->
			AID.fromString(idString)
				.map(txId -> respondWithTransactionLookupResult(request, txId))
				.orElseGet(() -> errorResponse(request, RpcError.INVALID_PARAMS, "Unable to recognize transaction ID")));
	}

	public JSONObject handleBuildTransaction(JSONObject request) {
		return withRequiredArrayParameter(request, "actions", (params, actions) ->
			ActionParser.parse(actions)
				.flatMap(steps -> submissionService.prepareTransaction(steps, safeString(params, "message")))
				.fold(
					failure -> errorResponse(request, RpcError.INVALID_PARAMS, failure.message()),
					value -> response(request, value.asJson())
				)
		);
	}

	public JSONObject handleFinalizeTransaction(JSONObject request) {
		return withRequiredParameters(
			request,
			Set.of("transaction", "signatureDER", "publicKeyOfSigner"),
			__ -> respondFinalizationResult(request)
		);
	}

	public JSONObject handleSubmitTransaction(JSONObject request) {
		return withRequiredParameters(
			request,
			Set.of("transaction", "signatureDER", "publicKeyOfSigner", "txID"),
			__ -> respondSubmissionResult(request)
		);
	}

	private JSONObject formatTransactionStatus(AID txId) {
		return transactionStatusService.getTransactionStatus(txId)
			.asJson(jsonObject().put("txID", txId));
	}

	private JSONObject respondWithTransactionLookupResult(JSONObject request, AID txId) {
		return highLevelApiService.getTransaction(txId)
			.fold(
				failure -> errorResponse(request, RpcError.INVALID_PARAMS, failure.message()),
				value -> response(request, value.asJson())
			);
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
				tuple -> tuple.map((cursor, transactions) -> response(request, jsonObject()
					.put("cursor", cursor.map(HighLevelApiHandler::asCursor).orElse(""))
					.put("transactions", fromList(transactions, TxHistoryEntry::asJson))))
			);
	}

	private JSONObject respondFinalizationResult(JSONObject request) {
		return Result.allOf(parseBlob(request), parseSignatureDer(request), parsePublicKey(request))
			.flatMap((blob, signature, publicKey) ->
						 toRecoverable(blob, signature, publicKey)
							 .flatMap(recoverable -> calculateTxId(blob, recoverable)))
			.fold(
				failure -> errorResponse(request, RpcError.INVALID_PARAMS, failure.message()),
				txId -> response(request, jsonObject().put("txID", txId.toString()))
			);
	}

	private JSONObject respondSubmissionResult(JSONObject request) {
		return Result.allOf(parseBlob(request), parseSignatureDer(request), parsePublicKey(request), parseTxId(request))
			.flatMap((blob, signature, publicKey, txId) ->
						 toRecoverable(blob, signature, publicKey)
							 .flatMap(recoverable -> submitTx(blob, recoverable, txId)))
			.fold(
				failure -> errorResponse(request, RpcError.INVALID_PARAMS, failure.message()),
				txId -> response(request, jsonObject().put("txID", txId.toString()))
			);
	}

	private Result<AID> submitTx(byte[] blob, ECDSASignature recoverable, AID txId) {
		//TODO: finish
	}

	private Result<AID> calculateTxId(byte[] blob, ECDSASignature recoverable) {
		//TODO: finish
	}

	private Result<ECDSASignature> toRecoverable(byte[] blob, ECDSASignature signature, ECPublicKey publicKey) {
		//TODO: finish
	}

	private Result<byte[]> parseBlob(JSONObject request) {
		try {
			var blob = Hex.decodeStrict(request.getJSONObject("transaction").getString("blob"));

			return Result.ok(blob);
		} catch (Exception e) {
			return Result.fail(e.getMessage());
		}
	}

	private Result<ECDSASignature> parseSignatureDer(JSONObject request) {
		try {
			var signature = Hex.decodeStrict(request.getString("signatureDER"));

			return Result.ok(ECDSASignature.decodeFromDER(signature));
		} catch (Exception e) {
			return Result.fail(e.getMessage());
		}
	}

	private Result<ECPublicKey> parsePublicKey(JSONObject request) {
		try {
			var pubKeyBytes = Hex.decodeStrict(request.getString("publicKeyOfSigner"));

			return Result.ok(ECPublicKey.fromBytes(pubKeyBytes));
		} catch (Exception e) {
			return Result.fail(e.getMessage());
		}
	}

	private Result<AID> parseTxId(JSONObject request) {
		try {
			return AID.fromBytes(Hex.decodeStrict(request.getString("txID")));
		} catch (Exception e) {
			return Result.fail(e.getMessage());
		}
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
}
