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

package com.radixdlt.client.lib.api.async;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bouncycastle.util.encoders.Hex;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.radixdlt.client.lib.api.AccountAddress;
import com.radixdlt.client.lib.api.NavigationCursor;
import com.radixdlt.client.lib.api.TransactionRequest;
import com.radixdlt.client.lib.api.ValidatorAddress;
import com.radixdlt.client.lib.dto.ApiConfiguration;
import com.radixdlt.client.lib.dto.ApiData;
import com.radixdlt.client.lib.dto.BuiltTransaction;
import com.radixdlt.client.lib.dto.Checkpoint;
import com.radixdlt.client.lib.dto.ConsensusConfiguration;
import com.radixdlt.client.lib.dto.ConsensusData;
import com.radixdlt.client.lib.dto.EpochData;
import com.radixdlt.client.lib.dto.FinalizedTransaction;
import com.radixdlt.client.lib.dto.JsonRpcRequest;
import com.radixdlt.client.lib.dto.JsonRpcResponse;
import com.radixdlt.client.lib.dto.LocalAccount;
import com.radixdlt.client.lib.dto.LocalValidatorInfo;
import com.radixdlt.client.lib.dto.MempoolConfiguration;
import com.radixdlt.client.lib.dto.MempoolData;
import com.radixdlt.client.lib.dto.NetworkConfiguration;
import com.radixdlt.client.lib.dto.NetworkData;
import com.radixdlt.client.lib.dto.NetworkId;
import com.radixdlt.client.lib.dto.NetworkPeers;
import com.radixdlt.client.lib.dto.NetworkStats;
import com.radixdlt.client.lib.dto.PortSelector;
import com.radixdlt.client.lib.dto.Proof;
import com.radixdlt.client.lib.dto.RadixEngineConfiguration;
import com.radixdlt.client.lib.dto.RadixEngineData;
import com.radixdlt.client.lib.dto.RpcMethod;
import com.radixdlt.client.lib.dto.StakePositions;
import com.radixdlt.client.lib.dto.SyncConfiguration;
import com.radixdlt.client.lib.dto.SyncData;
import com.radixdlt.client.lib.dto.TokenBalances;
import com.radixdlt.client.lib.dto.TokenInfo;
import com.radixdlt.client.lib.dto.TransactionDTO;
import com.radixdlt.client.lib.dto.TransactionHistory;
import com.radixdlt.client.lib.dto.TransactionStatusDTO;
import com.radixdlt.client.lib.dto.TxBlobDTO;
import com.radixdlt.client.lib.dto.TxDTO;
import com.radixdlt.client.lib.dto.UnstakePositions;
import com.radixdlt.client.lib.dto.ValidatorDTO;
import com.radixdlt.client.lib.dto.ValidatorsResponse;
import com.radixdlt.client.lib.dto.serializer.AccountAddressDeserializer;
import com.radixdlt.client.lib.dto.serializer.AccountAddressSerializer;
import com.radixdlt.client.lib.dto.serializer.ValidatorAddressDeserializer;
import com.radixdlt.client.lib.dto.serializer.ValidatorAddressSerializer;
import com.radixdlt.identifiers.AID;
import com.radixdlt.utils.functional.Failure;
import com.radixdlt.utils.functional.Result;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.security.KeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import static com.radixdlt.client.lib.api.ClientLibraryErrors.BASE_URL_IS_MANDATORY;
import static com.radixdlt.client.lib.dto.RpcMethod.ACCOUNT_BALANCES;
import static com.radixdlt.client.lib.dto.RpcMethod.ACCOUNT_HISTORY;
import static com.radixdlt.client.lib.dto.RpcMethod.ACCOUNT_INFO;
import static com.radixdlt.client.lib.dto.RpcMethod.ACCOUNT_STAKES;
import static com.radixdlt.client.lib.dto.RpcMethod.ACCOUNT_SUBMIT_SINGLE_STEP;
import static com.radixdlt.client.lib.dto.RpcMethod.ACCOUNT_UNSTAKES;
import static com.radixdlt.client.lib.dto.RpcMethod.API_CONFIGURATION;
import static com.radixdlt.client.lib.dto.RpcMethod.API_DATA;
import static com.radixdlt.client.lib.dto.RpcMethod.BFT_CONFIGURATION;
import static com.radixdlt.client.lib.dto.RpcMethod.BFT_DATA;
import static com.radixdlt.client.lib.dto.RpcMethod.CONSTRUCTION_BUILD;
import static com.radixdlt.client.lib.dto.RpcMethod.CONSTRUCTION_FINALIZE;
import static com.radixdlt.client.lib.dto.RpcMethod.CONSTRUCTION_SUBMIT;
import static com.radixdlt.client.lib.dto.RpcMethod.LEDGER_CHECKPOINTS;
import static com.radixdlt.client.lib.dto.RpcMethod.LEDGER_EPOCH_PROOF;
import static com.radixdlt.client.lib.dto.RpcMethod.LEDGER_PROOF;
import static com.radixdlt.client.lib.dto.RpcMethod.MEMPOOL_CONFIGURATION;
import static com.radixdlt.client.lib.dto.RpcMethod.MEMPOOL_DATA;
import static com.radixdlt.client.lib.dto.RpcMethod.NETWORK_CONFIG;
import static com.radixdlt.client.lib.dto.RpcMethod.NETWORK_DATA;
import static com.radixdlt.client.lib.dto.RpcMethod.NETWORK_DEMAND;
import static com.radixdlt.client.lib.dto.RpcMethod.NETWORK_ID;
import static com.radixdlt.client.lib.dto.RpcMethod.NETWORK_PEERS;
import static com.radixdlt.client.lib.dto.RpcMethod.NETWORK_THROUGHPUT;
import static com.radixdlt.client.lib.dto.RpcMethod.RADIX_ENGINE_CONFIGURATION;
import static com.radixdlt.client.lib.dto.RpcMethod.RADIX_ENGINE_DATA;
import static com.radixdlt.client.lib.dto.RpcMethod.SYNC_CONFIGURATION;
import static com.radixdlt.client.lib.dto.RpcMethod.SYNC_DATA;
import static com.radixdlt.client.lib.dto.RpcMethod.TOKEN_INFO;
import static com.radixdlt.client.lib.dto.RpcMethod.TOKEN_NATIVE;
import static com.radixdlt.client.lib.dto.RpcMethod.TRANSACTION_LOOKUP;
import static com.radixdlt.client.lib.dto.RpcMethod.TRANSACTION_STATUS;
import static com.radixdlt.client.lib.dto.RpcMethod.VALIDATION_CURRENT_EPOCH;
import static com.radixdlt.client.lib.dto.RpcMethod.VALIDATION_NEXT_EPOCH;
import static com.radixdlt.client.lib.dto.RpcMethod.VALIDATION_NODE_INFO;
import static com.radixdlt.client.lib.dto.RpcMethod.VALIDATORS_LIST;
import static com.radixdlt.client.lib.dto.RpcMethod.VALIDATORS_LOOKUP;
import static com.radixdlt.identifiers.CommonErrors.SSL_ALGORITHM_ERROR;
import static com.radixdlt.identifiers.CommonErrors.SSL_GENERAL_ERROR;
import static com.radixdlt.identifiers.CommonErrors.SSL_KEY_ERROR;
import static com.radixdlt.identifiers.CommonErrors.UNABLE_TO_DESERIALIZE;
import static com.radixdlt.networks.Network.LOCALNET;

import static java.util.Optional.ofNullable;

public class AsyncRadixApi implements RadixApi {
	private static final Logger log = LogManager.getLogger();
	private static final String CONTENT_TYPE = "Content-Type";
	private static final String APPLICATION_JSON = "application/json";
	private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);
	private static final ObjectMapper DEFAULT_OBJECT_MAPPER = createDefaultMapper();

	private final AtomicLong idCounter = new AtomicLong();

	private final String baseUrl;
	private final int primaryPort;
	private final int secondaryPort;
	private final HttpClient client;

	private Duration timeout = DEFAULT_TIMEOUT;
	private boolean doTrace = false;
	private ObjectMapper objectMapper;
	private int networkId = LOCALNET.getId();

	private final Network network = new Network() {
		@Override
		public Promise<NetworkId> id() {
			return call(request(NETWORK_ID), new TypeReference<>() {});
		}

		@Override
		public Promise<NetworkStats> throughput() {
			return call(request(NETWORK_THROUGHPUT), new TypeReference<>() {});
		}

		@Override
		public Promise<NetworkStats> demand() {
			return call(request(NETWORK_DEMAND), new TypeReference<>() {});
		}

		@Override
		public Promise<NetworkConfiguration> configuration() {
			return call(request(NETWORK_CONFIG), new TypeReference<>() {});
		}

		@Override
		public Promise<NetworkData> data() {
			return call(request(NETWORK_DATA), new TypeReference<>() {});
		}

		@Override
		public Promise<NetworkPeers> peers() {
			return call(request(NETWORK_PEERS), new TypeReference<>() {});
		}
	};

	private final Token token = new Token() {
		@Override
		public Promise<TokenInfo> describeNative() {
			return call(request(TOKEN_NATIVE), new TypeReference<>() {});
		}

		@Override
		public Promise<TokenInfo> describe(String rri) {
			return call(request(TOKEN_INFO, rri), new TypeReference<>() {});
		}
	};

	private final Transaction transaction = new Transaction() {
		@Override
		public Promise<BuiltTransaction> build(TransactionRequest request) {
			return call(
				request(CONSTRUCTION_BUILD, request.getActions(), request.getFeePayer(), request.getMessage()),
				new TypeReference<>() {}
			);
		}

		@Override
		public Promise<TxBlobDTO> finalize(FinalizedTransaction request, boolean immediateSubmit) {
			return call(
				request(CONSTRUCTION_FINALIZE, request.getBlob(), request.getSignature(), request.getPublicKey(), Boolean.toString(immediateSubmit)),
				new TypeReference<>() {}
			);
		}

		@Override
		public Promise<TxDTO> submit(TxBlobDTO request) {
			return call(
				request(CONSTRUCTION_SUBMIT, Hex.toHexString(request.getBlob()), request.getTxId()),
				new TypeReference<>() {}
			);
		}

		@Override
		public Promise<TransactionDTO> lookup(AID txId) {
			return call(request(TRANSACTION_LOOKUP, txId.toString()), new TypeReference<>() {});
		}

		@Override
		public Promise<TransactionStatusDTO> status(AID txId) {
			return call(request(TRANSACTION_STATUS, txId.toString()), new TypeReference<>() {});
		}
	};

	private final SingleAccount account = new SingleAccount() {
		@Override
		public Promise<TokenBalances> balances(AccountAddress address) {
			return call(request(ACCOUNT_BALANCES, address.toString(networkId)), new TypeReference<>() {});
		}

		@Override
		public Promise<TransactionHistory> history(
			AccountAddress address, int size, Optional<NavigationCursor> cursor
		) {
			var request = request(ACCOUNT_HISTORY, address.toString(networkId), size);
			cursor.ifPresent(cursorValue -> request.addParameters(cursorValue.value()));

			return call(request, new TypeReference<>() {});
		}

		@Override
		public Promise<List<StakePositions>> stakes(AccountAddress address) {
			return call(request(ACCOUNT_STAKES, address.toString(networkId)), new TypeReference<>() {});
		}

		@Override
		public Promise<List<UnstakePositions>> unstakes(AccountAddress address) {
			return call(request(ACCOUNT_UNSTAKES, address.toString(networkId)), new TypeReference<>() {});
		}
	};

	private final Validator validator = new Validator() {
		@Override
		public Promise<ValidatorsResponse> list(int size, Optional<NavigationCursor> cursor) {
			var request = request(VALIDATORS_LIST, size);
			cursor.ifPresent(cursorValue -> request.addParameters(cursorValue.value()));

			return call(request, new TypeReference<>() {});
		}

		@Override
		public Promise<ValidatorDTO> lookup(ValidatorAddress validatorAddress) {
			return call(request(VALIDATORS_LOOKUP, validatorAddress.toString(networkId)), new TypeReference<>() {});
		}
	};

	private final Local local = new Local() {
		@Override
		public Promise<LocalAccount> accountInfo() {
			return call(request(ACCOUNT_INFO), new TypeReference<>() {});
		}

		@Override
		public Promise<TxDTO> submitTxSingleStep(TransactionRequest request) {
			return call(
				request(ACCOUNT_SUBMIT_SINGLE_STEP, request.getActions(), request.getMessage()),
				new TypeReference<>() {}
			);
		}

		@Override
		public Promise<LocalValidatorInfo> validatorInfo() {
			return call(request(VALIDATION_NODE_INFO), new TypeReference<>() {});
		}

		@Override
		public Promise<EpochData> currentEpoch() {
			return call(request(VALIDATION_CURRENT_EPOCH), new TypeReference<>() {});
		}

		@Override
		public Promise<EpochData> nextEpoch() {
			return call(request(VALIDATION_NEXT_EPOCH), new TypeReference<>() {});
		}
	};

	private final Api api = new Api() {
		@Override
		public Promise<ApiConfiguration> configuration() {
			return call(request(API_CONFIGURATION), new TypeReference<>() {});
		}

		@Override
		public Promise<ApiData> data() {
			return call(request(API_DATA), new TypeReference<>() {});
		}
	};

	private final Consensus consensus = new Consensus() {
		@Override
		public Promise<ConsensusConfiguration> configuration() {
			return call(request(BFT_CONFIGURATION), new TypeReference<>() {});
		}

		@Override
		public Promise<ConsensusData> data() {
			return call(request(BFT_DATA), new TypeReference<>() {});
		}
	};

	private final Mempool mempool = new Mempool() {
		@Override
		public Promise<MempoolConfiguration> configuration() {
			return call(request(MEMPOOL_CONFIGURATION), new TypeReference<>() {});
		}

		@Override
		public Promise<MempoolData> data() {
			return call(request(MEMPOOL_DATA), new TypeReference<>() {});
		}
	};

	private final RadixEngine radixEngine = new RadixEngine() {
		@Override
		public Promise<RadixEngineConfiguration> configuration() {
			return call(request(RADIX_ENGINE_CONFIGURATION), new TypeReference<>() {});
		}

		@Override
		public Promise<RadixEngineData> data() {
			return call(request(RADIX_ENGINE_DATA), new TypeReference<>() {});
		}
	};

	private final Sync sync = new Sync() {
		@Override
		public Promise<SyncConfiguration> configuration() {
			return call(request(SYNC_CONFIGURATION), new TypeReference<>() {});
		}

		@Override
		public Promise<SyncData> data() {
			return call(request(SYNC_DATA), new TypeReference<>() {});
		}
	};

	private final Ledger ledger = new Ledger() {
		@Override
		public Promise<Proof> latest() {
			return call(request(LEDGER_PROOF), new TypeReference<>() {});
		}

		@Override
		public Promise<Proof> epoch() {
			return call(request(LEDGER_EPOCH_PROOF), new TypeReference<>() {});
		}

		@Override
		public Promise<Checkpoint> checkpoints() {
			return call(request(LEDGER_CHECKPOINTS), new TypeReference<>() {});
		}
	};

	private AsyncRadixApi(String baseUrl, int primaryPort, int secondaryPort, HttpClient client) {
		this.baseUrl = sanitize(baseUrl);
		this.primaryPort = primaryPort;
		this.secondaryPort = secondaryPort;
		this.client = client;
	}

	private static String sanitize(String baseUrl) {
		return baseUrl.endsWith("/")
			   ? baseUrl.substring(0, baseUrl.length() - 1)
			   : baseUrl;
	}

	static Promise<RadixApi> connect(String url, int primaryPort, int secondaryPort) {
		return buildHttpClient().fold(Promise::failure, client -> connect(url, primaryPort, secondaryPort, client));
	}

	static Promise<RadixApi> connect(String url, int primaryPort, int secondaryPort, HttpClient client) {
		return ofNullable(url)
			.map(baseUrl -> Result.ok(new AsyncRadixApi(baseUrl, primaryPort, secondaryPort, client)))
			.orElseGet(BASE_URL_IS_MANDATORY::result)
			.fold(Promise::<AsyncRadixApi>failure, Promise::ok)
			.flatMap(asyncRadixApi -> asyncRadixApi.network().id()
				.onSuccess(networkId -> asyncRadixApi.configureSerialization(networkId.getNetworkId()))
				.map(__ -> asyncRadixApi));
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
	public AsyncRadixApi withTrace() {
		doTrace = true;
		return this;
	}

	@Override
	public AsyncRadixApi withTimeout(Duration timeout) {
		this.timeout = timeout;
		return this;
	}

	private JsonRpcRequest request(RpcMethod rpcMethod, Object... parameters) {
		return JsonRpcRequest.create(rpcMethod, idCounter.incrementAndGet(), parameters);
	}

	private <T> Promise<T> call(JsonRpcRequest request, TypeReference<JsonRpcResponse<T>> typeReference) {
		return serialize(request)
			.onSuccess(this::trace)
			.map(value -> buildRequest(request, value))
			.map(httpRequest -> client.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofString()))
			.map(future -> Promise.<T>promise(promise -> future.thenAccept(body -> bodyHandler(body, promise, typeReference))))
			.fold(Promise::failure, promise -> promise);
	}

	private <T> void bodyHandler(
		HttpResponse<String> body,
		Promise<T> promise,
		TypeReference<JsonRpcResponse<T>> reference
	) {
		promise.resolve(deserialize(trace(body.body()), reference)
							.flatMap(response -> response.rawError() == null
												 ? Result.ok(response.rawResult())
												 : Result.fail(response.rawError().toFailure())));
	}

	private HttpRequest buildRequest(JsonRpcRequest request, String value) {
		return HttpRequest.newBuilder()
			.uri(buildUrl(request.rpcDetails()))
			.timeout(timeout)
			.header(CONTENT_TYPE, APPLICATION_JSON)
			.POST(BodyPublishers.ofString(value))
			.build();
	}

	private <T> T trace(T value) {
		if (doTrace) {
			log.debug(value.toString());
		}

		return value;
	}

	private Result<String> serialize(JsonRpcRequest request) {
		return Result.wrap(UNABLE_TO_DESERIALIZE, () -> objectMapper().writeValueAsString(request));
	}

	private <T> Result<JsonRpcResponse<T>> deserialize(String body, TypeReference<JsonRpcResponse<T>> typeReference) {
		return Result.wrap(UNABLE_TO_DESERIALIZE, () -> objectMapper().readValue(body, typeReference));
	}

	private URI buildUrl(RpcMethod rpcMethod) {
		var endPoint = rpcMethod.endPoint();
		var port = endPoint.portSelector() == PortSelector.PRIMARY
				   ? primaryPort
				   : secondaryPort;

		return URI.create(baseUrl + ":" + port + endPoint.path());
	}

	private static Result<HttpClient> buildHttpClient() {
		var props = System.getProperties();
		props.setProperty("jdk.internal.httpclient.disableHostnameVerification", "true");

		var trustAllCerts = new TrustManager[]{
			new X509TrustManager() {
				public X509Certificate[] getAcceptedIssuers() {
					return null;
				}

				public void checkClientTrusted(X509Certificate[] certs, String authType) { }

				public void checkServerTrusted(X509Certificate[] certs, String authType) { }
			}
		};

		return Result.wrap(
			AsyncRadixApi::decodeSslExceptions,
			() -> {
				var sc = SSLContext.getInstance("SSL");
				sc.init(null, trustAllCerts, new SecureRandom());
				return sc;
			}
		).map(sc -> HttpClient.newBuilder()
			.connectTimeout(DEFAULT_TIMEOUT)
			.sslContext(sc)
			.build());
	}

	private static Failure decodeSslExceptions(Throwable throwable) {
		if (throwable instanceof NoSuchAlgorithmException) {
			return SSL_KEY_ERROR.with(throwable.getMessage());
		}

		if (throwable instanceof KeyException) {
			return SSL_ALGORITHM_ERROR.with(throwable.getMessage());
		}

		return SSL_GENERAL_ERROR.with(throwable.getMessage());
	}

	private void configureSerialization(int networkId) {
		var module = new SimpleModule();
		module.addSerializer(AccountAddress.class, new AccountAddressSerializer(networkId));
		module.addDeserializer(AccountAddress.class, new AccountAddressDeserializer(networkId));
		module.addSerializer(ValidatorAddress.class, new ValidatorAddressSerializer(networkId));
		module.addDeserializer(ValidatorAddress.class, new ValidatorAddressDeserializer(networkId));
		objectMapper = createDefaultMapper().registerModule(module);
	}

	private ObjectMapper objectMapper() {
		return objectMapper == null ? DEFAULT_OBJECT_MAPPER : objectMapper;
	}

	private static ObjectMapper createDefaultMapper() {
		return new ObjectMapper().setSerializationInclusion(JsonInclude.Include.NON_ABSENT);
	}
}
