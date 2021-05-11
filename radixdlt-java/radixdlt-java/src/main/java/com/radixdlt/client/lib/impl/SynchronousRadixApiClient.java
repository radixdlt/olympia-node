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

package com.radixdlt.client.lib.impl;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.radixdlt.client.lib.api.AccountAddress;
import com.radixdlt.client.lib.api.NavigationCursor;
import com.radixdlt.client.lib.api.RadixApi;
import com.radixdlt.client.lib.api.TransactionRequest;
import com.radixdlt.client.lib.dto.BuiltTransactionDTO;
import com.radixdlt.client.lib.dto.FinalizedTransaction;
import com.radixdlt.client.lib.dto.JsonRpcRequest;
import com.radixdlt.client.lib.dto.JsonRpcResponse;
import com.radixdlt.client.lib.dto.NetworkIdDTO;
import com.radixdlt.client.lib.dto.NetworkStatsDTO;
import com.radixdlt.client.lib.dto.RpcMethod;
import com.radixdlt.client.lib.dto.StakePositionsDTO;
import com.radixdlt.client.lib.dto.TokenBalancesDTO;
import com.radixdlt.client.lib.dto.TokenInfoDTO;
import com.radixdlt.client.lib.dto.TransactionDTO;
import com.radixdlt.client.lib.dto.TransactionHistoryDTO;
import com.radixdlt.client.lib.dto.TransactionStatusDTO;
import com.radixdlt.client.lib.dto.TxDTO;
import com.radixdlt.client.lib.dto.UnstakePositionsDTO;
import com.radixdlt.client.lib.dto.ValidatorDTO;
import com.radixdlt.client.lib.dto.ValidatorsResponseDTO;
import com.radixdlt.identifiers.AID;
import com.radixdlt.utils.functional.Result;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import okhttp3.ConnectionSpec;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;

import static com.radixdlt.client.lib.api.ClientLibraryErrors.BASE_URL_IS_MANDATORY;
import static com.radixdlt.client.lib.api.ClientLibraryErrors.NO_CONTENT;
import static com.radixdlt.client.lib.api.ClientLibraryErrors.UNABLE_TO_READ_RESPONSE_BODY;
import static com.radixdlt.client.lib.dto.RpcMethod.BUILD_TRANSACTION;
import static com.radixdlt.client.lib.dto.RpcMethod.FINALIZE_TRANSACTION;
import static com.radixdlt.client.lib.dto.RpcMethod.LOOKUP_TRANSACTION;
import static com.radixdlt.client.lib.dto.RpcMethod.LOOKUP_VALIDATOR;
import static com.radixdlt.client.lib.dto.RpcMethod.NATIVE_TOKEN;
import static com.radixdlt.client.lib.dto.RpcMethod.NETWORK_ID;
import static com.radixdlt.client.lib.dto.RpcMethod.NETWORK_TRANSACTION_DEMAND;
import static com.radixdlt.client.lib.dto.RpcMethod.NETWORK_TRANSACTION_THROUGHPUT;
import static com.radixdlt.client.lib.dto.RpcMethod.STAKE_POSITIONS;
import static com.radixdlt.client.lib.dto.RpcMethod.STATUS_OF_TRANSACTION;
import static com.radixdlt.client.lib.dto.RpcMethod.SUBMIT_TRANSACTION;
import static com.radixdlt.client.lib.dto.RpcMethod.TOKEN_BALANCES;
import static com.radixdlt.client.lib.dto.RpcMethod.TOKEN_INFO;
import static com.radixdlt.client.lib.dto.RpcMethod.TRANSACTION_HISTORY;
import static com.radixdlt.client.lib.dto.RpcMethod.UNSTAKE_POSITIONS;
import static com.radixdlt.client.lib.dto.RpcMethod.VALIDATORS;
import static com.radixdlt.identifiers.CommonErrors.UNABLE_TO_DESERIALIZE;
import static com.radixdlt.utils.functional.Result.fromOptional;

import static java.util.Optional.ofNullable;

public class SynchronousRadixApiClient implements RadixApi {
	private static final MediaType MEDIA_TYPE = MediaType.parse("application/json");
	private static final ObjectMapper objectMapper;

	static {
		objectMapper = new ObjectMapper().setSerializationInclusion(JsonInclude.Include.NON_ABSENT);
	}

	private final AtomicLong idCounter = new AtomicLong();

	private final String baseUrl;
	private final OkHttpClient client;

	private SynchronousRadixApiClient(String baseUrl, OkHttpClient client) {
		this.baseUrl = sanitize(baseUrl);
		this.client = client;
	}

	private static String sanitize(String baseUrl) {
		return !baseUrl.endsWith("/")
			   ? baseUrl
			   : baseUrl.substring(0, baseUrl.length() - 1);
	}

	public static Result<SynchronousRadixApiClient> connect(String url) {
		return connect(url, createClient());
	}

	public static Result<SynchronousRadixApiClient> connect(String url, OkHttpClient client) {
		return ofNullable(url)
			.map(baseUrl -> Result.ok(new SynchronousRadixApiClient(baseUrl, client)))
			.orElseGet(() -> Result.fail(BASE_URL_IS_MANDATORY));
	}

	@Override
	public Result<TokenInfoDTO> nativeToken() {
		return call(request(NATIVE_TOKEN), new TypeReference<JsonRpcResponse<TokenInfoDTO>>() { });
	}

	@Override
	public Result<TokenInfoDTO> tokenInfo(String rri) {
		return call(request(TOKEN_INFO, rri), new TypeReference<JsonRpcResponse<TokenInfoDTO>>() { });
	}

	@Override
	public Result<TokenBalancesDTO> tokenBalances(AccountAddress address) {
		return call(request(TOKEN_BALANCES, address.toString()), new TypeReference<JsonRpcResponse<TokenBalancesDTO>>() { });
	}

	@Override
	public Result<TransactionHistoryDTO> transactionHistory(AccountAddress address, int size, Optional<NavigationCursor> cursor) {
		var request = request(TRANSACTION_HISTORY, address.toString(), size);
		cursor.ifPresent(cursorValue -> request.addParameters(cursorValue.value()));

		return call(request, new TypeReference<JsonRpcResponse<TransactionHistoryDTO>>() { });
	}

	@Override
	public Result<TransactionDTO> lookupTransaction(AID txId) {
		return call(request(LOOKUP_TRANSACTION, txId.toString()), new TypeReference<JsonRpcResponse<TransactionDTO>>() { });
	}

	@Override
	public Result<List<StakePositionsDTO>> stakePositions(AccountAddress address) {
		return call(request(STAKE_POSITIONS, address.toString()), new TypeReference<JsonRpcResponse<List<StakePositionsDTO>>>() { });
	}

	@Override
	public Result<List<UnstakePositionsDTO>> unstakePositions(AccountAddress address) {
		return call(request(UNSTAKE_POSITIONS, address.toString()), new TypeReference<JsonRpcResponse<List<UnstakePositionsDTO>>>() { });
	}

	@Override
	public Result<TransactionStatusDTO> statusOfTransaction(AID txId) {
		return call(request(STATUS_OF_TRANSACTION, txId.toString()), new TypeReference<JsonRpcResponse<TransactionStatusDTO>>() { });
	}

	@Override
	public Result<NetworkStatsDTO> networkTransactionThroughput() {
		return call(request(NETWORK_TRANSACTION_THROUGHPUT), new TypeReference<JsonRpcResponse<NetworkStatsDTO>>() { });
	}

	@Override
	public Result<NetworkStatsDTO> networkTransactionDemand() {
		return call(request(NETWORK_TRANSACTION_DEMAND), new TypeReference<JsonRpcResponse<NetworkStatsDTO>>() { });
	}

	@Override
	public Result<ValidatorsResponseDTO> validators(int size, Optional<NavigationCursor> cursor) {
		var request = request(VALIDATORS, size);
		cursor.ifPresent(cursorValue -> request.addParameters(cursorValue.value()));

		return call(request, new TypeReference<JsonRpcResponse<ValidatorsResponseDTO>>() { });
	}


	@Override
	public Result<ValidatorDTO> lookupValidator(String validatorAddress) {
		return call(request(LOOKUP_VALIDATOR, validatorAddress), new TypeReference<JsonRpcResponse<ValidatorDTO>>() { });
	}

	@Override
	public Result<BuiltTransactionDTO> buildTransaction(TransactionRequest request) {
		return call(
			request(BUILD_TRANSACTION, request.getActions(), request.getMessage()),
			new TypeReference<JsonRpcResponse<BuiltTransactionDTO>>() { }
		);
	}


	@Override
	public Result<NetworkIdDTO> networkId() {
		return call(request(NETWORK_ID), new TypeReference<JsonRpcResponse<NetworkIdDTO>>() { });
	}

	@Override
	public Result<TxDTO> finalizeTransaction(FinalizedTransaction request) {
		return call(
			request(FINALIZE_TRANSACTION, request.getBlob(), request.getSignature(), request.getPublicKey()),
			new TypeReference<JsonRpcResponse<TxDTO>>() { }
		);
	}

	@Override
	public Result<TxDTO> submitTransaction(FinalizedTransaction request) {
		return call(
			request(SUBMIT_TRANSACTION, request.getBlob(), request.getSignature(), request.getPublicKey(), request.getTxId()),
			new TypeReference<JsonRpcResponse<TxDTO>>() { }
		);
	}

	private JsonRpcRequest request(RpcMethod method, Object... parameters) {
		return JsonRpcRequest.create(method.method(), idCounter.incrementAndGet(), parameters);
	}

	private <T> Result<T> call(JsonRpcRequest request, TypeReference<JsonRpcResponse<T>> typeReference) {
		return serialize(request)
			.map(value -> RequestBody.create(MEDIA_TYPE, value))
			.flatMap(this::doCall)
			.flatMap(body -> deserialize(body, typeReference))
			.flatMap(response -> response.rawError() == null
								 ? Result.ok(response.rawResult())
								 : Result.fail(response.rawError().toFailure()));
	}

	private Result<String> serialize(JsonRpcRequest request) {
		return Result.wrap(UNABLE_TO_DESERIALIZE, () -> objectMapper.writeValueAsString(request));
	}

	private <T> Result<JsonRpcResponse<T>> deserialize(String body, TypeReference<JsonRpcResponse<T>> typeReference) {
		return Result.wrap(UNABLE_TO_DESERIALIZE, () -> objectMapper.readValue(body, typeReference));
	}

	private Result<String> doCall(RequestBody requestBody) {
		var request = buildRequest(requestBody);

		try (var response = client.newCall(request).execute(); var responseBody = response.body()) {
			return fromOptional(NO_CONTENT, ofNullable(responseBody))
				.flatMap(responseBody1 -> Result.wrap(UNABLE_TO_READ_RESPONSE_BODY, responseBody1::string));
		} catch (IOException e) {
			return UNABLE_TO_READ_RESPONSE_BODY.with(e.getMessage()).result();
		}
	}

	private Request buildRequest(RequestBody requestBody) {
		return new Request.Builder().url(baseUrl + "/rpc").post(requestBody).build();
	}

	private static OkHttpClient createClient() {
		return new OkHttpClient.Builder()
			.connectionSpecs(List.of(ConnectionSpec.CLEARTEXT))
			.connectTimeout(30, TimeUnit.SECONDS)
			.writeTimeout(30, TimeUnit.SECONDS)
			.readTimeout(30, TimeUnit.SECONDS)
			.pingInterval(30, TimeUnit.SECONDS)
			.build();
	}
}
