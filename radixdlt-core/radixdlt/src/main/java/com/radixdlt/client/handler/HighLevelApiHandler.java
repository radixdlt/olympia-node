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

import com.radixdlt.client.AccountAddress;
import com.radixdlt.client.service.NetworkInfoService;
import com.radixdlt.client.store.berkeley.UnstakeEntry;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.crypto.HashUtils;
import org.bouncycastle.util.encoders.Hex;
import org.json.JSONArray;
import org.json.JSONObject;
import com.radixdlt.api.JsonRpcUtil;

import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import com.radixdlt.client.ValidatorAddress;
import com.radixdlt.client.api.PreparedTransaction;
import com.radixdlt.client.api.TransactionAction;
import com.radixdlt.client.api.TxHistoryEntry;
import com.radixdlt.client.api.ValidatorInfoDetails;
import com.radixdlt.client.service.HighLevelApiService;
import com.radixdlt.client.service.SubmissionService;
import com.radixdlt.client.service.TransactionStatusService;
import com.radixdlt.client.service.ValidatorInfoService;
import com.radixdlt.client.store.TokenBalance;
import com.radixdlt.client.store.TokenDefinitionRecord;
import com.radixdlt.client.store.berkeley.BalanceEntry;
import com.radixdlt.crypto.ECDSASignature;
import com.radixdlt.crypto.ECKeyUtils;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.identifiers.AID;
import com.radixdlt.utils.functional.Result;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static com.radixdlt.api.JsonRpcUtil.ARRAY;
import static com.radixdlt.api.JsonRpcUtil.fromList;
import static com.radixdlt.api.JsonRpcUtil.invalidParamsError;
import static com.radixdlt.api.JsonRpcUtil.jsonObject;
import static com.radixdlt.api.JsonRpcUtil.response;
import static com.radixdlt.api.JsonRpcUtil.safeInteger;
import static com.radixdlt.api.JsonRpcUtil.safeString;
import static com.radixdlt.api.JsonRpcUtil.withRequiredArrayParameter;
import static com.radixdlt.api.JsonRpcUtil.withRequiredParameters;
import static com.radixdlt.api.JsonRpcUtil.withRequiredStringParameter;

import static com.radixdlt.utils.functional.Optionals.allOf;
import static com.radixdlt.utils.functional.Result.allOf;
import static com.radixdlt.utils.functional.Tuple.tuple;

public class HighLevelApiHandler {
	private final HighLevelApiService highLevelApiService;
	private final TransactionStatusService transactionStatusService;
	private final SubmissionService submissionService;
	private final ValidatorInfoService validatorInfoService;
	private final NetworkInfoService networkInfoService;

	private final byte magic;

	@Inject
	public HighLevelApiHandler(
		HighLevelApiService highLevelApiService,
		TransactionStatusService transactionStatusService,
		SubmissionService submissionService,
		ValidatorInfoService validatorInfoService,
		NetworkInfoService networkInfoService
	) {
		this.highLevelApiService = highLevelApiService;
		this.transactionStatusService = transactionStatusService;
		this.submissionService = submissionService;
		this.validatorInfoService = validatorInfoService;
		this.networkInfoService = networkInfoService;

		this.magic = (byte) highLevelApiService.getUniverseMagic();
	}

	public JSONObject handleUniverseMagic(JSONObject request) {
		return response(request, jsonObject().put("networkId", magic));
	}

	public JSONObject handleNativeToken(JSONObject request) {
		return highLevelApiService.getNativeTokenDescription()
			.map(TokenDefinitionRecord::asJson)
			.fold(failure -> invalidParamsError(request, failure), response -> response(request, response));
	}

	public JSONObject handleTokenInfo(JSONObject request) {
		return withRequiredStringParameter(
			request,
			(params, tokenId) -> highLevelApiService.getTokenDescription(tokenId)
				.map(TokenDefinitionRecord::asJson)
		);
	}

	public JSONObject handleTokenBalances(JSONObject request) {
		return withRequiredStringParameter(
			request,
			(params, address) -> AccountAddress.parseFunctional(address)
				.flatMap(key ->
							 highLevelApiService.getTokenBalances(key).map(v -> tuple(key, v)))
				.map(tuple -> tuple.map(this::formatTokenBalances))
		);
	}

	public JSONObject handleTransactionStatus(JSONObject request) {
		return withRequiredStringParameter(
			request,
			(params, idString) -> AID.fromString(idString)
				.map(txId -> transactionStatusService.getTransactionStatus(txId).asJson(formatTxId(txId)))
		);
	}

	public JSONObject handleLookupTransaction(JSONObject request) {
		return withRequiredStringParameter(
			request,
			(params, idString) -> AID.fromString(idString)
				.flatMap(txId -> highLevelApiService.getTransaction(txId).map(TxHistoryEntry::asJson))
		);
	}

	public JSONObject handleBuildTransaction(JSONObject request) {
		return withRequiredArrayParameter(request, (params, actions) ->
			highLevelApiService.parse(actions)
				.map(steps -> mergeMessageAction(params, steps))
				.flatMap(submissionService::prepareTransaction)
				.map(PreparedTransaction::asJson)
		);
	}

	public JSONObject handleTransactionHistory(JSONObject request) {
		return withRequiredParameters(
			request,
			List.of("address", "size"),
			List.of("cursor"),
			params -> respondWithTransactionHistory(params, parseInstantCursor(request))
		);
	}

	public JSONObject handleFinalizeTransaction(JSONObject request) {
		return withRequiredParameters(
			request,
			List.of("transaction", "signatureDER", "publicKeyOfSigner"),
			List.of(),
			this::respondFinalizationResult
		);
	}

	public JSONObject handleSubmitTransaction(JSONObject request) {
		return withRequiredParameters(
			request,
			List.of("transaction", "signatureDER", "publicKeyOfSigner", "txID"),
			List.of(),
			this::respondSubmissionResult
		);
	}

	public JSONObject handleValidators(JSONObject request) {
		return withRequiredParameters(
			request,
			List.of("size"),
			List.of("cursor"),
			params -> allOf(Result.ok(request), parseSize(params)).flatMap(this::formatValidators)
		);
	}

	public JSONObject handleStakePositions(JSONObject request) {
		return withRequiredStringParameter(
			request,
			(params, address) -> AccountAddress.parseFunctional(address)
				.flatMap(highLevelApiService::getStakePositions)
				.map(this::formatStakePositions)
		);
	}

	public JSONObject handleUnstakePositions(JSONObject request) {
		return withRequiredStringParameter(
			request,
			(params, address) -> AccountAddress.parseFunctional(address)
				.flatMap(highLevelApiService::getUnstakePositions)
				.map(this::formatUnstakePositions)
		);
	}

	public JSONObject handleLookupValidator(JSONObject request) {
		return withRequiredStringParameter(
			request,
			(params, address) -> ValidatorAddress.fromString(address)
				.flatMap(validatorInfoService::getValidator)
				.map(ValidatorInfoDetails::asJson)
		);
	}

	public JSONObject handleNetworkTransactionThroughput(JSONObject request) {
		return response(request, jsonObject().put("tps", networkInfoService.throughput()));
	}

	public JSONObject handleNetworkTransactionDemand(JSONObject request) {
		return response(request, jsonObject().put("tps", networkInfoService.demand()));
	}

	//-----------------------------------------------------------------------------------------------------
	// internal processing
	//-----------------------------------------------------------------------------------------------------

	private JSONObject formatUnstakePositions(List<UnstakeEntry> balances) {
		var array = fromList(balances, unstake ->
			jsonObject()
				.put("validator", ValidatorAddress.of(unstake.getValidator()))
				.put("amount", unstake.getAmount())
				.put("epochsUntil", unstake.getEpochsUntil())
				.put("withdrawTxID", unstake.getWithdrawTxId())
		);
		return jsonObject().put(ARRAY, array);
	}

	private Result<JSONObject> respondWithTransactionHistory(JSONObject params, Optional<Instant> cursor) {
		return allOf(parseAddress(params), parseSize(params), Result.ok(cursor))
			.flatMap(this::formatTransactionHistory);
	}

	private Result<JSONObject> respondFinalizationResult(JSONObject params) {
		return allOf(parseBlob(params), parseSignatureDer(params), parsePublicKey(params))
			.flatMap((blob, signature, publicKey) ->
						 toRecoverable(blob, signature, publicKey)
							 .flatMap(recoverable -> submissionService.calculateTxId(blob, recoverable)))
			.map(this::formatTxId);
	}

	private Result<JSONObject> respondSubmissionResult(JSONObject params) {
		return allOf(parseBlob(params), parseSignatureDer(params), parsePublicKey(params), parseTxId(params))
			.flatMap((blob, signature, publicKey, txId) ->
						 toRecoverable(blob, signature, publicKey)
							 .flatMap(recoverable -> submissionService.submitTx(blob, recoverable, txId)))
			.map(this::formatTxId);
	}

	private JSONObject formatTokenBalances(REAddr addr, List<TokenBalance> balances) {
		return jsonObject()
			.put("owner", AccountAddress.of(addr))
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

	private Result<JSONObject> formatTransactionHistory(REAddr address, int size, Optional<Instant> cursor) {
		return highLevelApiService
			.getTransactionHistory(address, size, cursor)
			.map(tuple -> tuple.map(this::formatHistoryResponse));
	}

	private Result<JSONObject> formatValidators(JSONObject request, int size) {
		return validatorInfoService
			.getValidators(size, parseAddressCursor(request))
			.map(tuple -> tuple.map(this::formatValidatorResponse));
	}

	private JSONObject formatTxId(AID txId) {
		return jsonObject().put("txID", txId);
	}

	private JSONObject formatHistoryResponse(Optional<Instant> cursor, List<TxHistoryEntry> transactions) {
		return jsonObject()
			.put("cursor", cursor.map(HighLevelApiHandler::asCursor).orElse(""))
			.put("transactions", fromList(transactions, TxHistoryEntry::asJson));
	}

	private JSONObject formatValidatorResponse(Optional<ECPublicKey> cursor, List<ValidatorInfoDetails> transactions) {
		return jsonObject()
			.put("cursor", cursor.map(ValidatorAddress::of).orElse(""))
			.put("validators", fromList(transactions, ValidatorInfoDetails::asJson));
	}

	private List<TransactionAction> mergeMessageAction(JSONArray params, List<TransactionAction> steps) {
		return params.length() == 1 ? steps : ImmutableList.<TransactionAction>builder()
			.addAll(steps)
			.add(TransactionAction.msg(params.getString(1)))
			.build();
	}

	private Result<ECDSASignature> toRecoverable(byte[] blob, ECDSASignature signature, ECPublicKey publicKey) {
		return ECKeyUtils.toRecoverable(signature, HashUtils.sha256(blob).asBytes(), publicKey);
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
		return Result.wrap(() -> {
			var signature = Hex.decodeStrict(request.getString("signatureDER"));
			return ECDSASignature.decodeFromDER(signature);
		});
	}

	private Result<ECPublicKey> parsePublicKey(JSONObject request) {
		return Result.wrap(() -> {
			var pubKeyBytes = Hex.decodeStrict(request.getString("publicKeyOfSigner"));
			return ECPublicKey.fromBytes(pubKeyBytes);
		});
	}

	private Result<AID> parseTxId(JSONObject request) {
		return Result.wrap(() -> Hex.decodeStrict(request.getString("txID")))
			.flatMap(AID::fromBytes);
	}

	private static String asCursor(Instant instant) {
		return "" + instant.getEpochSecond() + ":" + instant.getNano();
	}

	private static Optional<ECPublicKey> parseAddressCursor(JSONObject request) {
		return safeString(request, 1).flatMap(HighLevelApiHandler::parsePublicKey);
	}

	private static Optional<ECPublicKey> parsePublicKey(String address) {
		return ValidatorAddress.fromString(address).toOptional();
	}

	private static Optional<Instant> parseInstantCursor(JSONObject request) {
		var params = JsonRpcUtil.params(request);

		return safeString(request, 2).flatMap(HighLevelApiHandler::instantFromString);
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

	private static Result<REAddr> parseAddress(JSONObject params) {
		return AccountAddress.parseFunctional(params.getString("address"));
	}
}
