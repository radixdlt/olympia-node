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

import org.bouncycastle.util.encoders.Hex;
import org.json.JSONObject;
import org.radix.api.jsonrpc.JsonRpcUtil;
import org.radix.api.jsonrpc.JsonRpcUtil.RpcError;

import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import com.radixdlt.client.ValidatorAddress;
import com.radixdlt.client.api.TransactionAction;
import com.radixdlt.client.api.TxHistoryEntry;
import com.radixdlt.client.api.ValidatorInfoDetails;
import com.radixdlt.client.service.HighLevelApiService;
import com.radixdlt.client.service.SubmissionService;
import com.radixdlt.client.service.TransactionStatusService;
import com.radixdlt.client.service.ValidatorInfoService;
import com.radixdlt.constraintmachine.ConstraintMachine;
import com.radixdlt.crypto.ECDSASignature;
import com.radixdlt.crypto.ECKeyUtils;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.identifiers.AID;
import com.radixdlt.identifiers.RadixAddress;
import com.radixdlt.serialization.DeserializeException;
import com.radixdlt.utils.functional.Failure;
import com.radixdlt.utils.functional.Result;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.radix.api.jsonrpc.JsonRpcUtil.errorResponse;
import static org.radix.api.jsonrpc.JsonRpcUtil.fromList;
import static org.radix.api.jsonrpc.JsonRpcUtil.jsonObject;
import static org.radix.api.jsonrpc.JsonRpcUtil.response;
import static org.radix.api.jsonrpc.JsonRpcUtil.safeInteger;
import static org.radix.api.jsonrpc.JsonRpcUtil.withRequiredArrayParameter;
import static org.radix.api.jsonrpc.JsonRpcUtil.withRequiredParameters;
import static org.radix.api.jsonrpc.JsonRpcUtil.withRequiredStringParameter;

import static com.radixdlt.utils.functional.Optionals.allOf;

public class HighLevelApiHandler {
	private final HighLevelApiService highLevelApiService;
	private final TransactionStatusService transactionStatusService;
	private final SubmissionService submissionService;
	private final ValidatorInfoService validatorInfoService;
	private final byte magic;

	@Inject
	public HighLevelApiHandler(
		HighLevelApiService highLevelApiService,
		TransactionStatusService transactionStatusService,
		SubmissionService submissionService,
		ValidatorInfoService validatorInfoService
	) {
		this.highLevelApiService = highLevelApiService;
		this.transactionStatusService = transactionStatusService;
		this.submissionService = submissionService;
		this.validatorInfoService = validatorInfoService;
		this.magic = (byte) highLevelApiService.getUniverseMagic();
	}

	public JSONObject handleUniverseMagic(JSONObject request) {
		return response(request, jsonObject().put("networkId", magic));
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
			request,
			(params, tokenId) -> highLevelApiService.getTokenDescription(tokenId)
				.fold(
					failure -> toErrorResponse(request, failure),
					description -> response(request, description.asJson())
				)
		);
	}

	public JSONObject handleTokenBalances(JSONObject request) {
		return withRequiredStringParameter(
			request,
			(params, address) -> RadixAddress.fromString(address)
				.flatMap(this::formatTokenBalances)
				.fold(failure -> toErrorResponse(request, failure), response -> response(request, response))
		);
	}

	public JSONObject handleTransactionStatus(JSONObject request) {
		return withRequiredStringParameter(request, (params, idString) ->
			AID.fromString(idString)
				.map(txId -> response(request, formatTransactionStatus(txId)))
				.orElseGet(() -> errorResponse(request, RpcError.INVALID_PARAMS, "Unable to recognize transaction ID")));
	}

	public JSONObject handleLookupTransaction(JSONObject request) {
		return withRequiredStringParameter(request, (params, idString) ->
			AID.fromString(idString)
				.map(txId -> respondWithTransactionLookupResult(request, txId))
				.orElseGet(() -> errorResponse(request, RpcError.INVALID_PARAMS, "Unable to recognize transaction ID")));
	}

	public JSONObject handleBuildTransaction(JSONObject request) {
		return withRequiredArrayParameter(request, (params, actions) ->
			highLevelApiService.parse(actions)
				.map(steps ->
					params.length() == 1 ? steps : ImmutableList.<TransactionAction>builder()
						.addAll(steps)
						.add(TransactionAction.msg(params.getString(1)))
						.build()
				)
				.flatMap(submissionService::prepareTransaction)
				.fold(
					failure -> toErrorResponse(request, failure),
					value -> response(request, value.asJson())
				)
		);
	}

	public JSONObject handleTransactionHistory(JSONObject request) {
		return withRequiredParameters(
			request,
			List.of("address", "size"),
			List.of("cursor"),
			params -> respondWithTransactionHistory(request, params)
		);
	}

	public JSONObject handleFinalizeTransaction(JSONObject request) {
		return withRequiredParameters(
			request,
			List.of("transaction", "signatureDER", "publicKeyOfSigner"),
			List.of(),
			params -> respondFinalizationResult(params, request)
		);
	}

	public JSONObject handleSubmitTransaction(JSONObject request) {
		return withRequiredParameters(
			request,
			List.of("transaction", "signatureDER", "publicKeyOfSigner", "txID"),
			List.of(),
			params -> respondSubmissionResult(params, request)
		);
	}

	public JSONObject handleValidators(JSONObject request) {
		return withRequiredParameters(
			request,
			List.of("string"),
			List.of("cursor"),
			params -> respondWithValidators(request, params)
		);
	}

	private JSONObject respondWithTransactionLookupResult(JSONObject request, AID txId) {
		return highLevelApiService.getTransaction(txId)
			.fold(
				failure -> toErrorResponse(request, failure),
				value -> response(request, value.asJson())
			);
	}

	private JSONObject respondWithTransactionHistory(JSONObject request, JSONObject params) {
		return Result.allOf(Result.ok(request), parseAddress(params), parseSize(params))
			.flatMap(this::formatTransactionHistory)
			.fold(failure -> toErrorResponse(request, failure), v -> v);
	}

	private JSONObject respondWithValidators(JSONObject request, JSONObject params) {
		return Result.allOf(Result.ok(request), parseSize(params))
			.flatMap(this::formatValidators)
			.fold(failure -> toErrorResponse(request, failure), v -> v);
	}

	private JSONObject respondFinalizationResult(JSONObject params, JSONObject request) {
		return Result.allOf(parseBlob(params), parseSignatureDer(params), parsePublicKey(params))
			.flatMap((blob, signature, publicKey) ->
						 toRecoverable(blob, signature, publicKey)
							 .flatMap(recoverable -> submissionService.calculateTxId(blob, recoverable)))
			.fold(
				failure -> toErrorResponse(request, failure),
				txId -> response(request, jsonObject().put("txID", txId.toString()))
			);
	}

	private JSONObject respondSubmissionResult(JSONObject params, JSONObject request) {
		return Result.allOf(parseBlob(params), parseSignatureDer(params), parsePublicKey(params), parseTxId(params))
			.flatMap((blob, signature, publicKey, txId) ->
						 toRecoverable(blob, signature, publicKey)
							 .flatMap(recoverable -> submissionService.submitTx(blob, recoverable, txId)))
			.fold(
				failure -> toErrorResponse(request, failure),
				txId -> response(request, jsonObject().put("txID", txId.toString()))
			);
	}

	private Result<JSONObject> formatTokenBalances(RadixAddress radixAddress) {
		return highLevelApiService.getTokenBalances(radixAddress)
			.map(
				list -> jsonObject()
					.put("owner", radixAddress.toString())
					.put("tokenBalances", fromList(list, v -> v.asJson()))
			);
	}

	private JSONObject formatTransactionStatus(AID txId) {
		return transactionStatusService.getTransactionStatus(txId)
			.asJson(jsonObject().put("txID", txId));
	}

	private Result<JSONObject> formatTransactionHistory(JSONObject request, RadixAddress address, int size) {
		return highLevelApiService
			.getTransactionHistory(address, size, parseInstantCursor(request))
			.map(
				tuple -> tuple.map((cursor, transactions) -> response(request, jsonObject()
					.put("cursor", cursor.map(HighLevelApiHandler::asCursor).orElse(""))
					.put("transactions", fromList(transactions, TxHistoryEntry::asJson))))
			);
	}

	private Result<JSONObject> formatValidators(JSONObject request, int size) {
		return validatorInfoService
			.getValidators(size, parseAddressCursor(request))
			.map(
				tuple -> tuple.map((cursor, transactions) -> response(request, jsonObject()
					.put("cursor", cursor.map(ValidatorAddress::of).orElse(""))
					.put("validators", fromList(transactions, ValidatorInfoDetails::asJson))))
			);
	}

	private Result<ECDSASignature> toRecoverable(byte[] blob, ECDSASignature signature, ECPublicKey publicKey) {
		return ECKeyUtils.toRecoverable(signature, ConstraintMachine.computeHashToSignFromBytes(blob).asBytes(), publicKey);
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

	private static Optional<ECPublicKey> parseAddressCursor(JSONObject request) {
		var params = JsonRpcUtil.params(request);

		return params.isEmpty()
			? Optional.empty()
			: Optional.of(params.getString(0))
				.flatMap(address -> {
					try {
						return Optional.of(ValidatorAddress.parse(address));
					} catch (DeserializeException e) {
						return Optional.empty();
					}
				});
	}

	private static Optional<Instant> parseInstantCursor(JSONObject request) {
		var params = JsonRpcUtil.params(request);

		return params.isEmpty()
			   ? Optional.empty()
			   : Optional.of(params.getString(0))
				   .flatMap(HighLevelApiHandler::instantFromString);
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

	private static Result<Integer> parseSize(JSONObject params) {
		return safeInteger(params, "size")
			.map(Result::ok)
			.orElseGet(() -> Result.fail("Size parameter is not a valid integer"))
			.filter(value -> value > 0, "Size parameter must be greater than zero");
	}

	private static Result<RadixAddress> parseAddress(JSONObject params) {
		return RadixAddress.fromString(params.getString("address"));
	}

	private JSONObject toErrorResponse(JSONObject request, Failure failure) {
		return errorResponse(request, RpcError.INVALID_PARAMS, failure.message());
	}
}
