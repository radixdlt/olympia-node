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

package com.radixdlt.client.lib.api;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import com.radixdlt.client.lib.dto.extra.ApiConfigurationDTO;
import com.radixdlt.client.lib.dto.extra.ApiDataDTO;
import com.radixdlt.client.lib.dto.extra.CheckpointDTO;
import com.radixdlt.client.lib.dto.extra.ConsensusConfigurationDTO;
import com.radixdlt.client.lib.dto.extra.ConsensusDataDTO;
import com.radixdlt.client.lib.dto.extra.EpochDataDTO;
import com.radixdlt.client.lib.dto.extra.LocalAccountDTO;
import com.radixdlt.client.lib.dto.extra.LocalValidatorInfoDTO;
import com.radixdlt.client.lib.dto.extra.MempoolConfigurationDTO;
import com.radixdlt.client.lib.dto.extra.MempoolDataDTO;
import com.radixdlt.client.lib.dto.extra.NetworkConfigurationDTO;
import com.radixdlt.client.lib.dto.extra.NetworkDataDTO;
import com.radixdlt.client.lib.dto.extra.NetworkPeersDTO;
import com.radixdlt.client.lib.dto.extra.ProofDTO;
import com.radixdlt.client.lib.dto.extra.RadixEngineConfigurationDTO;
import com.radixdlt.client.lib.dto.extra.RadixEngineDataDTO;
import com.radixdlt.client.lib.dto.extra.SyncConfigurationDTO;
import com.radixdlt.client.lib.dto.extra.SyncDataDTO;
import com.radixdlt.client.lib.network.HttpClients;
import com.radixdlt.identifiers.AID;
import com.radixdlt.utils.functional.Result;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;

import static com.radixdlt.client.lib.api.ClientLibraryErrors.BASE_URL_IS_MANDATORY;
import static com.radixdlt.client.lib.api.ClientLibraryErrors.NO_CONTENT;
import static com.radixdlt.client.lib.api.ClientLibraryErrors.UNABLE_TO_READ_RESPONSE_BODY;
import static com.radixdlt.client.lib.dto.RpcMethod.ACCOUNT_INFO;
import static com.radixdlt.client.lib.dto.RpcMethod.ACCOUNT_SUBMIT_SINGLE_STEP;
import static com.radixdlt.client.lib.dto.RpcMethod.API_CONFIGURATION;
import static com.radixdlt.client.lib.dto.RpcMethod.API_DATA;
import static com.radixdlt.client.lib.dto.RpcMethod.BFT_CONFIGURATION;
import static com.radixdlt.client.lib.dto.RpcMethod.BFT_DATA;
import static com.radixdlt.client.lib.dto.RpcMethod.CONSTRUCTION_BUILD;
import static com.radixdlt.client.lib.dto.RpcMethod.CONSTRUCTION_FINALIZE;
import static com.radixdlt.client.lib.dto.RpcMethod.LEDGER_CHECKPOINTS;
import static com.radixdlt.client.lib.dto.RpcMethod.LEDGER_EPOCH_PROOF;
import static com.radixdlt.client.lib.dto.RpcMethod.LEDGER_PROOF;
import static com.radixdlt.client.lib.dto.RpcMethod.MEMPOOL_CONFIGURATION;
import static com.radixdlt.client.lib.dto.RpcMethod.MEMPOOL_DATA;
import static com.radixdlt.client.lib.dto.RpcMethod.NETWORK_DATA;
import static com.radixdlt.client.lib.dto.RpcMethod.NETWORK_PEERS;
import static com.radixdlt.client.lib.dto.RpcMethod.RADIX_ENGINE_CONFIGURATION;
import static com.radixdlt.client.lib.dto.RpcMethod.RADIX_ENGINE_DATA;
import static com.radixdlt.client.lib.dto.RpcMethod.SYNC_CONFIGURATION;
import static com.radixdlt.client.lib.dto.RpcMethod.SYNC_DATA;
import static com.radixdlt.client.lib.dto.RpcMethod.TRANSACTION_LOOKUP;
import static com.radixdlt.client.lib.dto.RpcMethod.VALIDATION_CURRENT_EPOCH;
import static com.radixdlt.client.lib.dto.RpcMethod.VALIDATION_NEXT_EPOCH;
import static com.radixdlt.client.lib.dto.RpcMethod.VALIDATION_NODE_INFO;
import static com.radixdlt.client.lib.dto.RpcMethod.VALIDATORS_LOOKUP;
import static com.radixdlt.client.lib.dto.RpcMethod.TOKEN_NATIVE;
import static com.radixdlt.client.lib.dto.RpcMethod.NETWORK_ID;
import static com.radixdlt.client.lib.dto.RpcMethod.NETWORK_DEMAND;
import static com.radixdlt.client.lib.dto.RpcMethod.NETWORK_THROUGHPUT;
import static com.radixdlt.client.lib.dto.RpcMethod.ACCOUNT_STAKES;
import static com.radixdlt.client.lib.dto.RpcMethod.TRANSACTION_STATUS;
import static com.radixdlt.client.lib.dto.RpcMethod.CONSTRUCTION_SUBMIT;
import static com.radixdlt.client.lib.dto.RpcMethod.ACCOUNT_BALANCES;
import static com.radixdlt.client.lib.dto.RpcMethod.TOKEN_INFO;
import static com.radixdlt.client.lib.dto.RpcMethod.ACCOUNT_HISTORY;
import static com.radixdlt.client.lib.dto.RpcMethod.ACCOUNT_UNSTAKES;
import static com.radixdlt.client.lib.dto.RpcMethod.VALIDATORS_LIST;
import static com.radixdlt.identifiers.CommonErrors.UNABLE_TO_DESERIALIZE;
import static com.radixdlt.utils.functional.Result.fromOptional;

import static java.util.Optional.ofNullable;

public class SynchronousRadixApiClient implements RadixApi {
	private static final Logger log = LogManager.getLogger();
	private static final MediaType MEDIA_TYPE = MediaType.parse("application/json");
	private static final ObjectMapper objectMapper;

	static {
		objectMapper = new ObjectMapper().setSerializationInclusion(JsonInclude.Include.NON_ABSENT);
	}

	private final AtomicLong idCounter = new AtomicLong();

	private final String baseUrl;
	private final OkHttpClient client;
	private boolean doTrace = false;

	private final Network network = new Network() {
		@Override
		public Result<NetworkIdDTO> id() {
			return call(request(NETWORK_ID), new TypeReference<>() {});
		}

		@Override
		public Result<NetworkStatsDTO> throughput() {
			return call(request(NETWORK_THROUGHPUT), new TypeReference<>() {});
		}

		@Override
		public Result<NetworkStatsDTO> demand() {
			return call(request(NETWORK_DEMAND), new TypeReference<>() {});
		}

		@Override
		public Result<NetworkConfigurationDTO> configuration() {
			return call(request(NETWORK_DEMAND), new TypeReference<>() {});
		}

		@Override
		public Result<NetworkDataDTO> data() {
			return call(request(NETWORK_DATA), new TypeReference<>() {});
		}

		@Override
		public Result<NetworkPeersDTO> peers() {
			return call(request(NETWORK_PEERS), new TypeReference<>() {});
		}
	};

	private final Token token = new Token() {
		@Override
		public Result<TokenInfoDTO> describeNative() {
			return call(request(TOKEN_NATIVE), new TypeReference<>() {});
		}

		@Override
		public Result<TokenInfoDTO> describe(String rri) {
			return call(request(TOKEN_INFO, rri), new TypeReference<>() {});
		}
	};

	private final Transaction transaction = new Transaction() {
		@Override
		public Result<BuiltTransactionDTO> build(TransactionRequest request) {
			return call(
				request(CONSTRUCTION_BUILD, request.getActions(), request.getMessage()),
				new TypeReference<>() {}
			);
		}

		@Override
		public Result<TxDTO> finalize(FinalizedTransaction request) {
			return call(
				request(CONSTRUCTION_FINALIZE, request.getBlob(), request.getSignature(), request.getPublicKey()),
				new TypeReference<>() {}
			);
		}

		@Override
		public Result<TxDTO> submit(FinalizedTransaction request) {
			return call(
				request(CONSTRUCTION_SUBMIT, request.getBlob(), request.getSignature(), request.getPublicKey(), request.getTxId()),
				new TypeReference<>() {}
			);
		}

		@Override
		public Result<TransactionDTO> lookup(AID txId) {
			return call(request(TRANSACTION_LOOKUP, txId.toString()), new TypeReference<>() {});
		}

		@Override
		public Result<TransactionStatusDTO> status(AID txId) {
			return call(request(TRANSACTION_STATUS, txId.toString()), new TypeReference<>() {});
		}
	};

	private final SingleAccount account = new SingleAccount() {
		@Override
		public Result<TokenBalancesDTO> balances(AccountAddress address) {
			return call(request(ACCOUNT_BALANCES, address.toString()), new TypeReference<>() {});
		}

		@Override
		public Result<TransactionHistoryDTO> history(
			AccountAddress address, int size, Optional<NavigationCursor> cursor
		) {
			var request = request(ACCOUNT_HISTORY, address.toString(), size);
			cursor.ifPresent(cursorValue -> request.addParameters(cursorValue.value()));

			return call(request, new TypeReference<>() {});
		}

		@Override
		public Result<List<StakePositionsDTO>> stakes(AccountAddress address) {
			return call(request(ACCOUNT_STAKES, address.toString()), new TypeReference<>() {});
		}

		@Override
		public Result<List<UnstakePositionsDTO>> unstakes(AccountAddress address) {
			return call(request(ACCOUNT_UNSTAKES, address.toString()), new TypeReference<>() {});
		}
	};

	private final Validator validator = new Validator() {
		@Override
		public Result<ValidatorsResponseDTO> list(int size, Optional<NavigationCursor> cursor) {
			var request = request(VALIDATORS_LIST, size);
			cursor.ifPresent(cursorValue -> request.addParameters(cursorValue.value()));

			return call(request, new TypeReference<>() {});
		}

		@Override
		public Result<ValidatorDTO> lookup(String validatorAddress) {
			return call(request(VALIDATORS_LOOKUP, validatorAddress), new TypeReference<>() {});
		}
	};

	private final Local local = new Local() {
		@Override
		public Result<LocalAccountDTO> accountInfo() {
			return call(request(ACCOUNT_INFO), new TypeReference<>() {});
		}

		@Override
		public Result<TxDTO> submitTxSingleStep() {
			return call(request(ACCOUNT_SUBMIT_SINGLE_STEP), new TypeReference<>() {});
		}

		@Override
		public Result<LocalValidatorInfoDTO> validatorInfo() {
			return call(request(VALIDATION_NODE_INFO), new TypeReference<>() {});
		}

		@Override
		public Result<EpochDataDTO> currentEpoch() {
			return call(request(VALIDATION_CURRENT_EPOCH), new TypeReference<>() {});
		}

		@Override
		public Result<EpochDataDTO> nextEpoch() {
			return call(request(VALIDATION_NEXT_EPOCH), new TypeReference<>() {});
		}
	};

	private final Api api = new Api() {
		@Override
		public Result<ApiConfigurationDTO> configuration() {
			return call(request(API_CONFIGURATION), new TypeReference<>() {});
		}

		@Override
		public Result<ApiDataDTO> data() {
			return call(request(API_DATA), new TypeReference<>() {});
		}
	};

	private final Consensus consensus = new Consensus() {
		@Override
		public Result<ConsensusConfigurationDTO> configuration() {
			return call(request(BFT_CONFIGURATION), new TypeReference<>() {});
		}

		@Override
		public Result<ConsensusDataDTO> data() {
			return call(request(BFT_DATA), new TypeReference<>() {});
		}
	};

	private final Mempool mempool = new Mempool() {
		@Override
		public Result<MempoolConfigurationDTO> configuration() {
			return call(request(MEMPOOL_CONFIGURATION), new TypeReference<>() {});
		}

		@Override
		public Result<MempoolDataDTO> data() {
			return call(request(MEMPOOL_DATA), new TypeReference<>() {});
		}
	};
	private final RadixEngine radixEngine = new RadixEngine() {
		@Override
		public Result<RadixEngineConfigurationDTO> configuration() {
			return call(request(RADIX_ENGINE_CONFIGURATION), new TypeReference<>() {});
		}

		@Override
		public Result<RadixEngineDataDTO> data() {
			return call(request(RADIX_ENGINE_DATA), new TypeReference<>() {});
		}
	};

	private final Sync sync = new Sync() {
		@Override
		public Result<SyncConfigurationDTO> configuration() {
			return call(request(SYNC_CONFIGURATION), new TypeReference<>() {});
		}

		@Override
		public Result<SyncDataDTO> data() {
			return call(request(SYNC_DATA), new TypeReference<>() {});
		}
	};

	private final Ledger ledger = new Ledger() {
		@Override
		public Result<ProofDTO> latest() {
			return call(request(LEDGER_PROOF), new TypeReference<>() {});
		}

		@Override
		public Result<ProofDTO> epoch() {
			return call(request(LEDGER_EPOCH_PROOF), new TypeReference<>() {});
		}

		@Override
		public Result<CheckpointDTO> checkpoints() {
			return call(request(LEDGER_CHECKPOINTS), new TypeReference<>() {});
		}
	};

	private SynchronousRadixApiClient(String baseUrl, OkHttpClient client) {
		this.baseUrl = sanitize(baseUrl);
		this.client = client;
	}

	private static String sanitize(String baseUrl) {
		return !baseUrl.endsWith("/")
			   ? baseUrl
			   : baseUrl.substring(0, baseUrl.length() - 1);
	}

	static Result<SynchronousRadixApiClient> connect(String url) {
		return connect(url, HttpClients.getSslAllTrustingClient());
	}

	static Result<SynchronousRadixApiClient> connect(String url, OkHttpClient client) {
		return ofNullable(url)
			.map(baseUrl -> Result.ok(new SynchronousRadixApiClient(baseUrl, client)))
			.orElseGet(BASE_URL_IS_MANDATORY::result);
	}

	@Override
	public Network network() {
		return network;
	}

	@Override
	public Transaction transaction() {
		return transaction;
	}

	@Override
	public Token token() {
		return token;
	}

	@Override
	public Local local() {
		return local;
	}

	@Override
	public SingleAccount account() {
		return account;
	}

	@Override
	public Validator validator() {
		return validator;
	}

	@Override
	public Api api() {
		return api;
	}

	@Override
	public Consensus consensus() {
		return consensus;
	}

	@Override
	public Mempool mempool() {
		return mempool;
	}

	@Override
	public RadixEngine radixEngine() {
		return radixEngine;
	}

	@Override
	public Sync sync() {
		return sync;
	}

	@Override
	public Ledger ledger() {
		return ledger;
	}

	@Override
	public SynchronousRadixApiClient withTrace() {
		doTrace = true;
		return this;
	}

	private JsonRpcRequest request(RpcMethod method, Object... parameters) {
		return JsonRpcRequest.create(method.method(), method.path(), idCounter.incrementAndGet(), parameters);
	}

	private <T> Result<T> call(JsonRpcRequest request, TypeReference<JsonRpcResponse<T>> typeReference) {
		return serialize(request)
			.onSuccess(this::trace)
			.map(value -> RequestBody.create(MEDIA_TYPE, value))
			.flatMap(requestBody -> doCall(request.path(), requestBody))
			.onSuccess(this::trace)
			.flatMap(body -> deserialize(body, typeReference))
			.flatMap(response -> response.rawError() == null
								 ? Result.ok(response.rawResult())
								 : Result.fail(response.rawError().toFailure()));
	}

	private void trace(Object value) {
		if (doTrace) {
			log.debug(value.toString());
		}
	}

	private Result<String> serialize(JsonRpcRequest request) {
		return Result.wrap(UNABLE_TO_DESERIALIZE, () -> objectMapper.writeValueAsString(request));
	}

	private <T> Result<JsonRpcResponse<T>> deserialize(String body, TypeReference<JsonRpcResponse<T>> typeReference) {
		return Result.wrap(UNABLE_TO_DESERIALIZE, () -> objectMapper.readValue(body, typeReference));
	}

	private Result<String> doCall(String path, RequestBody requestBody) {
		var request = buildRequest(path, requestBody);

		try (var response = client.newCall(request).execute(); var responseBody = response.body()) {
			return fromOptional(NO_CONTENT, ofNullable(responseBody))
				.flatMap(responseBody1 -> Result.wrap(UNABLE_TO_READ_RESPONSE_BODY, responseBody1::string));
		} catch (IOException e) {
			return UNABLE_TO_READ_RESPONSE_BODY.with(e.getMessage()).result();
		}
	}

	private Request buildRequest(String path, RequestBody requestBody) {
		return new Request.Builder().url(baseUrl + path).post(requestBody).build();
	}
}
