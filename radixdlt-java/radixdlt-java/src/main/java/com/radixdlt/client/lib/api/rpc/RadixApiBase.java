/* Copyright 2021 Radix DLT Ltd incorporated in England.
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

package com.radixdlt.client.lib.api.rpc;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bouncycastle.util.encoders.Hex;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.radixdlt.client.lib.api.AccountAddress;
import com.radixdlt.client.lib.api.NavigationCursor;
import com.radixdlt.client.lib.api.NodeAddress;
import com.radixdlt.client.lib.api.TransactionRequest;
import com.radixdlt.client.lib.api.ValidatorAddress;
import com.radixdlt.client.lib.api.sync.RadixApi;
import com.radixdlt.client.lib.dto.AddressBookEntry;
import com.radixdlt.client.lib.dto.ApiConfiguration;
import com.radixdlt.client.lib.dto.ApiData;
import com.radixdlt.client.lib.dto.BuiltTransaction;
import com.radixdlt.client.lib.dto.Checkpoint;
import com.radixdlt.client.lib.dto.ConsensusConfiguration;
import com.radixdlt.client.lib.dto.ConsensusData;
import com.radixdlt.client.lib.dto.EpochData;
import com.radixdlt.client.lib.dto.FinalizedTransaction;
import com.radixdlt.client.lib.dto.ForkDetails;
import com.radixdlt.client.lib.dto.LocalAccount;
import com.radixdlt.client.lib.dto.LocalValidatorInfo;
import com.radixdlt.client.lib.dto.MempoolConfiguration;
import com.radixdlt.client.lib.dto.MempoolData;
import com.radixdlt.client.lib.dto.NetworkConfiguration;
import com.radixdlt.client.lib.dto.NetworkData;
import com.radixdlt.client.lib.dto.NetworkId;
import com.radixdlt.client.lib.dto.NetworkPeer;
import com.radixdlt.client.lib.dto.NetworkStats;
import com.radixdlt.client.lib.dto.Proof;
import com.radixdlt.client.lib.dto.RadixEngineData;
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
import com.radixdlt.client.lib.dto.serializer.ECPublicKeyDeserializer;
import com.radixdlt.client.lib.dto.serializer.ECPublicKeySerializer;
import com.radixdlt.client.lib.dto.serializer.NodeAddressDeserializer;
import com.radixdlt.client.lib.dto.serializer.NodeAddressSerializer;
import com.radixdlt.client.lib.dto.serializer.ValidatorAddressDeserializer;
import com.radixdlt.client.lib.dto.serializer.ValidatorAddressSerializer;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.identifiers.AID;
import com.radixdlt.utils.functional.Failure;
import com.radixdlt.utils.functional.Result;

import java.io.IOException;
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
import static com.radixdlt.client.lib.api.ClientLibraryErrors.NETWORK_IO_ERROR;
import static com.radixdlt.client.lib.api.ClientLibraryErrors.OPERATION_INTERRUPTED;
import static com.radixdlt.client.lib.api.ClientLibraryErrors.UNKNOWN_ERROR;
import static com.radixdlt.client.lib.api.rpc.RpcMethod.ACCOUNT_BALANCES;
import static com.radixdlt.client.lib.api.rpc.RpcMethod.ACCOUNT_HISTORY;
import static com.radixdlt.client.lib.api.rpc.RpcMethod.ACCOUNT_INFO;
import static com.radixdlt.client.lib.api.rpc.RpcMethod.ACCOUNT_STAKES;
import static com.radixdlt.client.lib.api.rpc.RpcMethod.ACCOUNT_SUBMIT_SINGLE_STEP;
import static com.radixdlt.client.lib.api.rpc.RpcMethod.ACCOUNT_UNSTAKES;
import static com.radixdlt.client.lib.api.rpc.RpcMethod.API_CONFIGURATION;
import static com.radixdlt.client.lib.api.rpc.RpcMethod.API_DATA;
import static com.radixdlt.client.lib.api.rpc.RpcMethod.BFT_CONFIGURATION;
import static com.radixdlt.client.lib.api.rpc.RpcMethod.BFT_DATA;
import static com.radixdlt.client.lib.api.rpc.RpcMethod.CONSTRUCTION_BUILD;
import static com.radixdlt.client.lib.api.rpc.RpcMethod.CONSTRUCTION_FINALIZE;
import static com.radixdlt.client.lib.api.rpc.RpcMethod.CONSTRUCTION_SUBMIT;
import static com.radixdlt.client.lib.api.rpc.RpcMethod.LEDGER_CHECKPOINTS;
import static com.radixdlt.client.lib.api.rpc.RpcMethod.LEDGER_EPOCH_PROOF;
import static com.radixdlt.client.lib.api.rpc.RpcMethod.LEDGER_PROOF;
import static com.radixdlt.client.lib.api.rpc.RpcMethod.MEMPOOL_CONFIGURATION;
import static com.radixdlt.client.lib.api.rpc.RpcMethod.MEMPOOL_DATA;
import static com.radixdlt.client.lib.api.rpc.RpcMethod.NETWORK_ADDRESS_BOOK;
import static com.radixdlt.client.lib.api.rpc.RpcMethod.NETWORK_CONFIG;
import static com.radixdlt.client.lib.api.rpc.RpcMethod.NETWORK_DATA;
import static com.radixdlt.client.lib.api.rpc.RpcMethod.NETWORK_DEMAND;
import static com.radixdlt.client.lib.api.rpc.RpcMethod.NETWORK_ID;
import static com.radixdlt.client.lib.api.rpc.RpcMethod.NETWORK_PEERS;
import static com.radixdlt.client.lib.api.rpc.RpcMethod.NETWORK_THROUGHPUT;
import static com.radixdlt.client.lib.api.rpc.RpcMethod.RADIX_ENGINE_CONFIGURATION;
import static com.radixdlt.client.lib.api.rpc.RpcMethod.RADIX_ENGINE_DATA;
import static com.radixdlt.client.lib.api.rpc.RpcMethod.SYNC_CONFIGURATION;
import static com.radixdlt.client.lib.api.rpc.RpcMethod.SYNC_DATA;
import static com.radixdlt.client.lib.api.rpc.RpcMethod.TOKEN_INFO;
import static com.radixdlt.client.lib.api.rpc.RpcMethod.TOKEN_NATIVE;
import static com.radixdlt.client.lib.api.rpc.RpcMethod.TRANSACTION_LOOKUP;
import static com.radixdlt.client.lib.api.rpc.RpcMethod.TRANSACTION_STATUS;
import static com.radixdlt.client.lib.api.rpc.RpcMethod.VALIDATION_CURRENT_EPOCH;
import static com.radixdlt.client.lib.api.rpc.RpcMethod.VALIDATION_NODE_INFO;
import static com.radixdlt.client.lib.api.rpc.RpcMethod.VALIDATORS_LIST;
import static com.radixdlt.client.lib.api.rpc.RpcMethod.VALIDATORS_LOOKUP;
import static com.radixdlt.identifiers.CommonErrors.SSL_ALGORITHM_ERROR;
import static com.radixdlt.identifiers.CommonErrors.SSL_GENERAL_ERROR;
import static com.radixdlt.identifiers.CommonErrors.SSL_KEY_ERROR;
import static com.radixdlt.identifiers.CommonErrors.UNABLE_TO_DESERIALIZE;
import static com.radixdlt.networks.Network.LOCALNET;

public abstract class RadixApiBase {
	private static final Logger log = LogManager.getLogger();

	private static final String AUTH_HEADER = "Authorization";
	private static final String CONTENT_TYPE = "Content-Type";
	private static final String APPLICATION_JSON = "application/json";
	private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);
	private static final ObjectMapper DEFAULT_OBJECT_MAPPER = createDefaultMapper();

	private final AtomicLong idCounter = new AtomicLong();
	private final String baseUrl;
	private final int primaryPort;
	private final int secondaryPort;
	private final HttpClient client;
	private final Optional<String> authHeader;

	private Duration timeout = DEFAULT_TIMEOUT;
	private boolean doTrace = false;
	private ObjectMapper objectMapper;
	private int networkId = LOCALNET.getId();

	protected RadixApiBase(
		String baseUrl,
		int primaryPort,
		int secondaryPort,
		HttpClient client,
		Optional<BasicAuth> authentication
	) {
		this.baseUrl = sanitize(baseUrl);
		this.primaryPort = primaryPort;
		this.secondaryPort = secondaryPort;
		this.client = client;
		this.authHeader = authentication.map(BasicAuth::asHeader);
	}

	private static String sanitize(String baseUrl) {
		return baseUrl.endsWith("/")
			   ? baseUrl.substring(0, baseUrl.length() - 1)
			   : baseUrl;
	}

	protected void enableTrace() {
		doTrace = true;
	}

	protected void setTimeout(Duration timeout) {
		this.timeout = timeout;
	}

	protected JsonRpcRequest request(RpcMethod rpcMethod, Object... parameters) {
		return JsonRpcRequest.create(rpcMethod, idCounter.incrementAndGet(), parameters);
	}

	protected HttpRequest buildRequest(JsonRpcRequest request, String value) {
		var requestBuilder = HttpRequest.newBuilder()
			.uri(buildUrl(request.rpcDetails()))
			.timeout(timeout)
			.header(CONTENT_TYPE, APPLICATION_JSON);

		authHeader.ifPresent(header -> requestBuilder.header(AUTH_HEADER, header));

		return requestBuilder
			.POST(BodyPublishers.ofString(value))
			.build();
	}

	protected  <T> T trace(T value) {
		if (doTrace) {
			log.debug(value.toString());
		}

		return value;
	}

	protected int networkId() {
		return networkId;
	}

	protected HttpClient client() {
		return client;
	}

	protected Result<String> serialize(JsonRpcRequest request) {
		return Result.wrap(UNABLE_TO_DESERIALIZE, () -> objectMapper().writeValueAsString(request));
	}

	protected <T> Result<JsonRpcResponse<T>> deserialize(String body, TypeReference<JsonRpcResponse<T>> typeReference) {
		return Result.wrap(UNABLE_TO_DESERIALIZE, () -> objectMapper().readValue(body, typeReference));
	}

	protected static Result<HttpClient> buildHttpClient() {
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
			RadixApiBase::decodeSslExceptions,
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

	protected void configureSerialization(int networkId) {
		var module = new SimpleModule()
			.addSerializer(ValidatorAddress.class, new ValidatorAddressSerializer(networkId))
			.addSerializer(AccountAddress.class, new AccountAddressSerializer(networkId))
			.addSerializer(NodeAddress.class, new NodeAddressSerializer(networkId))
			.addSerializer(ECPublicKey.class, new ECPublicKeySerializer())
			.addDeserializer(AccountAddress.class, new AccountAddressDeserializer(networkId))
			.addDeserializer(ValidatorAddress.class, new ValidatorAddressDeserializer(networkId))
			.addDeserializer(NodeAddress.class, new NodeAddressDeserializer(networkId))
			.addDeserializer(ECPublicKey.class, new ECPublicKeyDeserializer());
		objectMapper = createDefaultMapper().registerModule(module);
	}

	private URI buildUrl(RpcMethod rpcMethod) {
		var endPoint = rpcMethod.endPoint();
		var port = endPoint.portSelector() == PortSelector.PRIMARY
				   ? primaryPort
				   : secondaryPort;

		return URI.create(baseUrl + ":" + port + endPoint.path());
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

	private ObjectMapper objectMapper() {
		return objectMapper == null ? DEFAULT_OBJECT_MAPPER : objectMapper;
	}

	private static ObjectMapper createDefaultMapper() {
		return new ObjectMapper().setSerializationInclusion(JsonInclude.Include.NON_ABSENT);
	}
}
