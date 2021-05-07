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

import org.json.JSONObject;

import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import com.radixdlt.client.api.PreparedTransaction;
import com.radixdlt.client.api.TransactionAction;
import com.radixdlt.client.api.TxHistoryEntry;
import com.radixdlt.client.api.ValidatorInfoDetails;
import com.radixdlt.client.service.HighLevelApiService;
import com.radixdlt.client.service.NetworkInfoService;
import com.radixdlt.client.service.SubmissionService;
import com.radixdlt.client.service.TransactionStatusService;
import com.radixdlt.client.service.ValidatorInfoService;
import com.radixdlt.client.store.TokenBalance;
import com.radixdlt.client.store.TokenDefinitionRecord;
import com.radixdlt.client.store.berkeley.BalanceEntry;
import com.radixdlt.client.store.berkeley.UnstakeEntry;
import com.radixdlt.crypto.ECDSASignature;
import com.radixdlt.crypto.ECKeyUtils;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.crypto.HashUtils;
import com.radixdlt.identifiers.AID;
import com.radixdlt.identifiers.AccountAddress;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.identifiers.ValidatorAddress;
import com.radixdlt.utils.functional.Result;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static com.radixdlt.api.JsonRpcUtil.ARRAY;
import static com.radixdlt.api.JsonRpcUtil.fromList;
import static com.radixdlt.api.JsonRpcUtil.invalidParamsError;
import static com.radixdlt.api.JsonRpcUtil.jsonObject;
import static com.radixdlt.api.JsonRpcUtil.response;
import static com.radixdlt.api.JsonRpcUtil.safeArray;
import static com.radixdlt.api.JsonRpcUtil.safeBlob;
import static com.radixdlt.api.JsonRpcUtil.safeInteger;
import static com.radixdlt.api.JsonRpcUtil.safeObject;
import static com.radixdlt.api.JsonRpcUtil.safeString;
import static com.radixdlt.api.JsonRpcUtil.withRequiredParameters;
import static com.radixdlt.api.JsonRpcUtil.withRequiredStringParameter;
import static com.radixdlt.client.api.ApiErrors.INVALID_PAGE_SIZE;
import static com.radixdlt.client.api.ApiErrors.INVALID_PUBLIC_KEY;
import static com.radixdlt.client.api.ApiErrors.INVALID_SIGNATURE_DER;
import static com.radixdlt.utils.functional.Optionals.allOf;
import static com.radixdlt.utils.functional.Result.allOf;
import static com.radixdlt.utils.functional.Result.ok;
import static com.radixdlt.utils.functional.Result.wrap;
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
			.fold(failure -> invalidParamsError(request, failure.message()), response -> response(request, response));
	}

	public JSONObject handleTokenInfo(JSONObject request) {
		return withRequiredStringParameter(
			request,
			"rri",
			(tokenId) -> highLevelApiService.getTokenDescription(tokenId)
				.map(TokenDefinitionRecord::asJson)
		);
	}

	public JSONObject handleTokenBalances(JSONObject request) {
		return withRequiredStringParameter(
			request,
			"address",
			(address) -> AccountAddress.parseFunctional(address)
				.flatMap(key -> highLevelApiService.getTokenBalances(key).map(v -> tuple(key, v)))
				.map(tuple -> tuple.map(HighLevelApiHandler::formatTokenBalances))
		);
	}

	public JSONObject handleTransactionStatus(JSONObject request) {
		return withRequiredStringParameter(
			request,
			"txID",
			(idString) -> AID.fromString(idString)
				.map(txId -> transactionStatusService.getTransactionStatus(txId).asJson(formatTxId(txId)))
		);
	}

	public JSONObject handleLookupTransaction(JSONObject request) {
		return withRequiredStringParameter(
			request,
			"txID",
			(idString) -> AID.fromString(idString)
				.flatMap(txId -> highLevelApiService.getTransaction(txId).map(TxHistoryEntry::asJson))
		);
	}

	public JSONObject handleBuildTransaction(JSONObject request) {
		return withRequiredParameters(
			request,
			List.of("actions"),
			List.of("message"),
			params -> safeArray(params, "actions").flatMap(
				actions -> highLevelApiService.parse(actions)
					.map(steps -> mergeMessageAction(params, steps))
					.flatMap(submissionService::prepareTransaction)
					.map(PreparedTransaction::asJson)
			)
		);
	}

	public JSONObject handleTransactionHistory(JSONObject request) {
		return withRequiredParameters(
			request,
			List.of("address", "size"),
			List.of("cursor"),
			params -> allOf(parseAddress(params), parseSize(params), ok(parseInstantCursor(params)))
				.flatMap(highLevelApiService::getTransactionHistory)
				.map(tuple -> tuple.map(HighLevelApiHandler::formatHistoryResponse))
		);
	}

	public JSONObject handleFinalizeTransaction(JSONObject request) {
		return withRequiredParameters(
			request,
			List.of("transaction", "signatureDER", "publicKeyOfSigner"),
			List.of(),
			params -> allOf(parseBlob(params), parseSignatureDer(params), parsePublicKey(params))
				.flatMap((blob, signature, publicKey) -> toRecoverable(blob, signature, publicKey)
					.flatMap(recoverable -> submissionService.calculateTxId(blob, recoverable)))
				.map(HighLevelApiHandler::formatTxId)
		);
	}

	public JSONObject handleSubmitTransaction(JSONObject request) {
		return withRequiredParameters(
			request,
			List.of("transaction", "signatureDER", "publicKeyOfSigner", "txID"),
			List.of(),
			params -> allOf(parseBlob(params), parseSignatureDer(params), parsePublicKey(params), parseTxId(params))
				.flatMap((blob, signature, publicKey, txId) -> toRecoverable(blob, signature, publicKey)
					.flatMap(recoverable -> submissionService.submitTx(blob, recoverable, txId)))
				.map(HighLevelApiHandler::formatTxId)
		);
	}

	public JSONObject handleValidators(JSONObject request) {
		return withRequiredParameters(
			request,
			List.of("size"),
			List.of("cursor"),
			params -> allOf(parseSize(params), ok(parseAddressCursor(params)))
				.flatMap((size, cursor) -> validatorInfoService
					.getValidators(size, cursor)
					.map(tuple -> tuple.map(HighLevelApiHandler::formatValidatorResponse)))
		);
	}

	public JSONObject handleStakePositions(JSONObject request) {
		return withRequiredStringParameter(
			request,
			"address",
			(address) -> AccountAddress.parseFunctional(address)
				.flatMap(highLevelApiService::getStakePositions)
				.map(this::formatStakePositions)
		);
	}

	public JSONObject handleUnstakePositions(JSONObject request) {
		return withRequiredStringParameter(
			request,
			"address",
			(address) -> AccountAddress.parseFunctional(address)
				.flatMap(highLevelApiService::getUnstakePositions)
				.map(HighLevelApiHandler::formatUnstakePositions)
		);
	}

	public JSONObject handleLookupValidator(JSONObject request) {
		return withRequiredStringParameter(
			request,
			"validatorAddress",
			(address) -> ValidatorAddress.fromString(address)
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

	private static JSONObject formatUnstakePositions(List<UnstakeEntry> balances) {
		var array = fromList(balances, unstake ->
			jsonObject()
				.put("validator", ValidatorAddress.of(unstake.getValidator()))
				.put("amount", unstake.getAmount())
				.put("epochsUntil", unstake.getEpochsUntil())
				.put("withdrawTxID", unstake.getWithdrawTxId())
		);
		return jsonObject().put(ARRAY, array);
	}

	private static JSONObject formatTokenBalances(REAddr addr, List<TokenBalance> balances) {
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

	private static JSONObject formatTxId(AID txId) {
		return jsonObject().put("txID", txId);
	}

	private static JSONObject formatHistoryResponse(Optional<Instant> cursor, List<TxHistoryEntry> transactions) {
		return jsonObject()
			.put("cursor", cursor.map(HighLevelApiHandler::asCursor).orElse(""))
			.put("transactions", fromList(transactions, TxHistoryEntry::asJson));
	}

	private static JSONObject formatValidatorResponse(Optional<ECPublicKey> cursor, List<ValidatorInfoDetails> transactions) {
		return jsonObject()
			.put("cursor", cursor.map(ValidatorAddress::of).orElse(""))
			.put("validators", fromList(transactions, ValidatorInfoDetails::asJson));
	}

	private static List<TransactionAction> mergeMessageAction(JSONObject params, List<TransactionAction> steps) {
		return safeString(params, "message")
			.fold(
				__ -> steps,
				message -> ImmutableList.<TransactionAction>builder()
					.addAll(steps)
					.add(TransactionAction.msg(message))
					.build()
			);
	}

	private static Result<ECDSASignature> toRecoverable(byte[] blob, ECDSASignature signature, ECPublicKey publicKey) {
		return ECKeyUtils.toRecoverable(signature, HashUtils.sha256(blob).asBytes(), publicKey);
	}

	private static String asCursor(Instant instant) {
		return "" + instant.getEpochSecond() + ":" + instant.getNano();
	}

	private static Result<byte[]> parseBlob(JSONObject params) {
		return safeObject(params, "transaction")
			.flatMap(txObj -> safeBlob(txObj, "blob"));
	}

	private static Result<ECDSASignature> parseSignatureDer(JSONObject params) {
		return safeBlob(params, "signatureDER")
			.flatMap(param -> wrap(INVALID_SIGNATURE_DER, () -> ECDSASignature.decodeFromDER(param)));
	}

	private static Result<ECPublicKey> parsePublicKey(JSONObject params) {
		return safeBlob(params, "publicKeyOfSigner")
			.flatMap(param -> wrap(INVALID_PUBLIC_KEY, () -> ECPublicKey.fromBytes(param)));
	}

	private static Result<AID> parseTxId(JSONObject params) {
		return safeString(params, "txID").flatMap(AID::fromString);
	}

	private static Optional<ECPublicKey> parseAddressCursor(JSONObject params) {
		return safeString(params, "cursor")
			.toOptional()
			.flatMap(HighLevelApiHandler::parsePublicKey);
	}

	private static Optional<ECPublicKey> parsePublicKey(String address) {
		return ValidatorAddress.fromString(address).toOptional();
	}

	private static Optional<Instant> parseInstantCursor(JSONObject params) {
		return safeString(params, "cursor")
			.toOptional()
			.flatMap(source -> Optional.of(source.split(":"))
				.filter(v -> v.length == 2)
				.flatMap(HighLevelApiHandler::parseInstant));
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
