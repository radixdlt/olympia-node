/* Copyright 2021 Radix Publishing Ltd incorporated in Jersey (Channel Islands).
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

package com.radixdlt.client.lib.api.sync;

import com.fasterxml.jackson.core.type.TypeReference;
import com.radixdlt.api.rpc.JsonRpcRequest;
import com.radixdlt.api.rpc.JsonRpcResponse;
import com.radixdlt.api.rpc.RpcMethodDescriptor;
import com.radixdlt.api.rpc.dto.AddressBookEntry;
import com.radixdlt.api.rpc.dto.ApiConfiguration;
import com.radixdlt.api.rpc.dto.ApiData;
import com.radixdlt.api.rpc.dto.BuiltTransaction;
import com.radixdlt.api.rpc.dto.Checkpoint;
import com.radixdlt.api.rpc.dto.ConsensusConfiguration;
import com.radixdlt.api.rpc.dto.ConsensusData;
import com.radixdlt.api.rpc.dto.EpochData;
import com.radixdlt.api.rpc.dto.FinalizedTransaction;
import com.radixdlt.api.rpc.dto.ForkDetails;
import com.radixdlt.api.rpc.dto.LocalAccount;
import com.radixdlt.api.rpc.dto.LocalValidatorInfo;
import com.radixdlt.api.rpc.dto.MempoolConfiguration;
import com.radixdlt.api.rpc.dto.MempoolData;
import com.radixdlt.api.rpc.dto.NetworkConfiguration;
import com.radixdlt.api.rpc.dto.NetworkData;
import com.radixdlt.api.rpc.dto.NetworkId;
import com.radixdlt.api.rpc.dto.NetworkPeer;
import com.radixdlt.api.rpc.dto.NetworkStats;
import com.radixdlt.api.rpc.dto.Proof;
import com.radixdlt.api.rpc.dto.RadixEngineData;
import com.radixdlt.api.rpc.dto.StakePositions;
import com.radixdlt.api.rpc.dto.SyncConfiguration;
import com.radixdlt.api.rpc.dto.SyncData;
import com.radixdlt.api.rpc.dto.TokenBalances;
import com.radixdlt.api.rpc.dto.TokenInfo;
import com.radixdlt.api.rpc.dto.TransactionDTO;
import com.radixdlt.api.rpc.dto.TransactionHistory;
import com.radixdlt.api.rpc.dto.TransactionStatusDTO;
import com.radixdlt.api.rpc.dto.TransactionsDTO;
import com.radixdlt.api.rpc.dto.TxBlobDTO;
import com.radixdlt.api.rpc.dto.TxDTO;
import com.radixdlt.api.rpc.dto.UnstakePositions;
import com.radixdlt.api.rpc.dto.ValidatorDTO;
import com.radixdlt.api.rpc.dto.ValidatorsResponse;
import com.radixdlt.api.rpc.parameter.MethodParameters;
import com.radixdlt.api.types.AccountAddress;
import com.radixdlt.api.types.NavigationCursor;
import com.radixdlt.api.types.TransactionRequest;
import com.radixdlt.api.types.ValidatorAddress;
import com.radixdlt.client.lib.api.rpc.BasicAuth;
import com.radixdlt.client.lib.api.rpc.RadixApiBase;
import com.radixdlt.identifiers.AID;
import com.radixdlt.networks.Addressing;
import com.radixdlt.utils.functional.Failure;
import com.radixdlt.utils.functional.Result;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

import static com.radixdlt.api.rpc.RpcMethodDescriptor.AccountGetBalancesMethod;
import static com.radixdlt.api.rpc.RpcMethodDescriptor.AccountGetTransactionHistoryMethod;
import static com.radixdlt.api.rpc.RpcMethodDescriptor.AccountGetInfoMethod;
import static com.radixdlt.api.rpc.RpcMethodDescriptor.AccountGetStakePositionsMethod;
import static com.radixdlt.api.rpc.RpcMethodDescriptor.AccountSubmitTransactionSingleStepMethod;
import static com.radixdlt.api.rpc.RpcMethodDescriptor.AccountGetUnstakePositionsMethod;
import static com.radixdlt.api.rpc.RpcMethodDescriptor.ApiGetConfigurationMethod;
import static com.radixdlt.api.rpc.RpcMethodDescriptor.ApiGetDataMethod;
import static com.radixdlt.api.rpc.RpcMethodDescriptor.BftGetConfigurationMethod;
import static com.radixdlt.api.rpc.RpcMethodDescriptor.BftGetDataMethod;
import static com.radixdlt.api.rpc.RpcMethodDescriptor.ConstructionBuildTransactionMethod;
import static com.radixdlt.api.rpc.RpcMethodDescriptor.ConstructionFinalizeTransactionMethod;
import static com.radixdlt.api.rpc.RpcMethodDescriptor.ConstructionSubmitTransactionMethod;
import static com.radixdlt.api.rpc.RpcMethodDescriptor.CheckpointsGetCheckpointsMethod;
import static com.radixdlt.api.rpc.RpcMethodDescriptor.LedgerGetLatestEpochProofMethod;
import static com.radixdlt.api.rpc.RpcMethodDescriptor.LedgerGetLatestProofMethod;
import static com.radixdlt.api.rpc.RpcMethodDescriptor.MempoolGetConfigurationMethod;
import static com.radixdlt.api.rpc.RpcMethodDescriptor.MempoolGetDataMethod;
import static com.radixdlt.api.rpc.RpcMethodDescriptor.NetworkingGetAddressBookMethod;
import static com.radixdlt.api.rpc.RpcMethodDescriptor.NetworkingGetConfigurationMethod;
import static com.radixdlt.api.rpc.RpcMethodDescriptor.NetworkingGetDataMethod;
import static com.radixdlt.api.rpc.RpcMethodDescriptor.NetworkGetDemandMethod;
import static com.radixdlt.api.rpc.RpcMethodDescriptor.NetworkGetIdMethod;
import static com.radixdlt.api.rpc.RpcMethodDescriptor.NetworkingGetPeersMethod;
import static com.radixdlt.api.rpc.RpcMethodDescriptor.NetworkGetThroughputMethod;
import static com.radixdlt.api.rpc.RpcMethodDescriptor.RadixEngineGetConfigurationMethod;
import static com.radixdlt.api.rpc.RpcMethodDescriptor.RadixEngineGetDataMethod;
import static com.radixdlt.api.rpc.RpcMethodDescriptor.SyncGetConfigurationMethod;
import static com.radixdlt.api.rpc.RpcMethodDescriptor.SyncGetDataMethod;
import static com.radixdlt.api.rpc.RpcMethodDescriptor.TokensGetInfoMethod;
import static com.radixdlt.api.rpc.RpcMethodDescriptor.TokensGetNativeTokenMethod;
import static com.radixdlt.api.rpc.RpcMethodDescriptor.GetTransactionsMethod;
import static com.radixdlt.api.rpc.RpcMethodDescriptor.TransactionsLookupTransactionMethod;
import static com.radixdlt.api.rpc.RpcMethodDescriptor.TransactionsGetTransactionStatusMethod;
import static com.radixdlt.api.rpc.RpcMethodDescriptor.ValidationGetCurrentEpochDataMethod;
import static com.radixdlt.api.rpc.RpcMethodDescriptor.ValidationGetNodeInfoMethod;
import static com.radixdlt.api.rpc.RpcMethodDescriptor.ValidatorsGetNextEpochSetMethod;
import static com.radixdlt.api.rpc.RpcMethodDescriptor.ValidatorsLookupValidatorMethod;
import static com.radixdlt.errors.ClientErrors.INTERRUPTED_OPERATION;
import static com.radixdlt.errors.ClientErrors.IO_ERROR;
import static com.radixdlt.errors.ClientErrors.MISSING_BASE_URL;
import static com.radixdlt.errors.InternalErrors.UNKNOWN;

import static java.util.Optional.ofNullable;

public class SyncRadixApi extends RadixApiBase implements RadixApi {
	private final Network network = new Network() {
		@Override
		public Result<NetworkId> id() {
			return call(NetworkGetIdMethod.INSTANCE, NetworkGetIdMethod.INSTANCE.create(nextId()));
		}

		@Override
		public Result<NetworkStats> throughput() {
			return call(NetworkGetThroughputMethod.INSTANCE, NetworkGetThroughputMethod.INSTANCE.create(nextId()));
		}

		@Override
		public Result<NetworkStats> demand() {
			return call(NetworkGetDemandMethod.INSTANCE, NetworkGetDemandMethod.INSTANCE.create(nextId()));
		}

		@Override
		public Result<NetworkConfiguration> configuration() {
			return call(NetworkingGetConfigurationMethod.INSTANCE, NetworkingGetConfigurationMethod.INSTANCE.create(nextId()));
		}

		@Override
		public Result<NetworkData> data() {
			return call(NetworkingGetDataMethod.INSTANCE, NetworkingGetDataMethod.INSTANCE.create(nextId()));
		}

		@Override
		public Result<List<NetworkPeer>> peers() {
			return call(NetworkingGetPeersMethod.INSTANCE, NetworkingGetPeersMethod.INSTANCE.create(nextId()));
		}

		@Override
		public Result<List<AddressBookEntry>> addressBook() {
			return call(NetworkingGetAddressBookMethod.INSTANCE, NetworkingGetAddressBookMethod.INSTANCE.create(nextId()));
		}
	};

	private final Token token = new Token() {
		@Override
		public Result<TokenInfo> describeNative() {
			return call(TokensGetNativeTokenMethod.INSTANCE, TokensGetNativeTokenMethod.INSTANCE.create(nextId()));
		}

		@Override
		public Result<TokenInfo> describe(String rri) {
			return call(TokensGetInfoMethod.INSTANCE, TokensGetInfoMethod.INSTANCE.create(nextId(), rri));
		}
	};

	private final Transaction transaction = new Transaction() {
		@Override
		public Result<BuiltTransaction> build(TransactionRequest request) {
			return call(ConstructionBuildTransactionMethod.INSTANCE, ConstructionBuildTransactionMethod.INSTANCE.create(nextId(), request));
		}

		@Override
		public Result<TxBlobDTO> finalize(FinalizedTransaction request, boolean immediateSubmit) {
			return call(ConstructionFinalizeTransactionMethod.INSTANCE,
						ConstructionFinalizeTransactionMethod.INSTANCE.create(nextId(), request, immediateSubmit));
		}

		@Override
		public Result<TxDTO> submit(TxBlobDTO request) {
			return call(ConstructionSubmitTransactionMethod.INSTANCE, ConstructionSubmitTransactionMethod.INSTANCE.create(nextId(), request));
		}

		@Override
		public Result<TransactionDTO> lookup(AID txId) {
			return call(TransactionsLookupTransactionMethod.INSTANCE, TransactionsLookupTransactionMethod.INSTANCE.create(nextId(), txId));
		}

		@Override
		public Result<TransactionStatusDTO> status(AID txId) {
			return call(TransactionsGetTransactionStatusMethod.INSTANCE, TransactionsGetTransactionStatusMethod.INSTANCE.create(nextId(), txId));
		}

		@Override
		public Result<TransactionsDTO> list(long limit, OptionalLong offset) {
			return call(GetTransactionsMethod.INSTANCE, GetTransactionsMethod.INSTANCE.create(nextId(), limit, offset));
		}
	};

	private final SingleAccount account = new SingleAccount() {
		@Override
		public Result<TokenBalances> balances(AccountAddress address) {
			return call(AccountGetBalancesMethod.INSTANCE, AccountGetBalancesMethod.INSTANCE.create(nextId(), address));
		}

		@Override
		public Result<TransactionHistory> history(
			AccountAddress address, int size, OptionalLong nextOffset, boolean verbose
		) {
			return call(AccountGetTransactionHistoryMethod.INSTANCE,
						AccountGetTransactionHistoryMethod.INSTANCE.create(nextId(), address, size, nextOffset, verbose));
		}

		@Override
		public Result<List<StakePositions>> stakes(AccountAddress address) {
			return call(AccountGetStakePositionsMethod.INSTANCE, AccountGetStakePositionsMethod.INSTANCE.create(nextId(), address));
		}

		@Override
		public Result<List<UnstakePositions>> unstakes(AccountAddress address) {
			return call(AccountGetUnstakePositionsMethod.INSTANCE, AccountGetUnstakePositionsMethod.INSTANCE.create(nextId(), address));
		}
	};

	private final Validator validator = new Validator() {
		@Override
		public Result<ValidatorsResponse> list(long size, Optional<NavigationCursor> cursor) {
			return call(ValidatorsGetNextEpochSetMethod.INSTANCE, ValidatorsGetNextEpochSetMethod.INSTANCE.create(nextId(), size, cursor));
		}

		@Override
		public Result<ValidatorDTO> lookup(ValidatorAddress address) {
			return call(ValidatorsLookupValidatorMethod.INSTANCE, ValidatorsLookupValidatorMethod.INSTANCE.create(nextId(), address));
		}
	};

	private final Local local = new Local() {
		@Override
		public Result<LocalAccount> accountInfo() {
			return call(AccountGetInfoMethod.INSTANCE, AccountGetInfoMethod.INSTANCE.create(nextId()));
		}

		@Override
		public Result<TxDTO> submitTxSingleStep(TransactionRequest request) {
			return call(AccountSubmitTransactionSingleStepMethod.INSTANCE,
						AccountSubmitTransactionSingleStepMethod.INSTANCE.create(nextId(), request));
		}

		@Override
		public Result<LocalValidatorInfo> validatorInfo() {
			return call(ValidationGetNodeInfoMethod.INSTANCE, ValidationGetNodeInfoMethod.INSTANCE.create(nextId()));
		}

		@Override
		public Result<EpochData> currentEpoch() {
			return call(ValidationGetCurrentEpochDataMethod.INSTANCE, ValidationGetCurrentEpochDataMethod.INSTANCE.create(nextId()));
		}
	};

	private final Api api = new Api() {
		@Override
		public Result<ApiConfiguration> configuration() {
			return call(ApiGetConfigurationMethod.INSTANCE, ApiGetConfigurationMethod.INSTANCE.create(nextId()));
		}

		@Override
		public Result<ApiData> data() {
			return call(ApiGetDataMethod.INSTANCE, ApiGetDataMethod.INSTANCE.create(nextId()));
		}
	};

	private final Consensus consensus = new Consensus() {
		@Override
		public Result<ConsensusConfiguration> configuration() {
			return call(BftGetConfigurationMethod.INSTANCE, BftGetConfigurationMethod.INSTANCE.create(nextId()));
		}

		@Override
		public Result<ConsensusData> data() {
			return call(BftGetDataMethod.INSTANCE, BftGetDataMethod.INSTANCE.create(nextId()));
		}
	};

	private final Mempool mempool = new Mempool() {
		@Override
		public Result<MempoolConfiguration> configuration() {
			return call(MempoolGetConfigurationMethod.INSTANCE, MempoolGetConfigurationMethod.INSTANCE.create(nextId()));
		}

		@Override
		public Result<MempoolData> data() {
			return call(MempoolGetDataMethod.INSTANCE, MempoolGetDataMethod.INSTANCE.create(nextId()));
		}
	};

	private final RadixEngine radixEngine = new RadixEngine() {
		@Override
		public Result<List<ForkDetails>> configuration() {
			return call(RadixEngineGetConfigurationMethod.INSTANCE, RadixEngineGetConfigurationMethod.INSTANCE.create(nextId()));
		}

		@Override
		public Result<RadixEngineData> data() {
			return call(RadixEngineGetDataMethod.INSTANCE, RadixEngineGetDataMethod.INSTANCE.create(nextId()));
		}
	};

	private final Sync sync = new Sync() {
		@Override
		public Result<SyncConfiguration> configuration() {
			return call(SyncGetConfigurationMethod.INSTANCE, SyncGetConfigurationMethod.INSTANCE.create(nextId()));
		}

		@Override
		public Result<SyncData> data() {
			return call(SyncGetDataMethod.INSTANCE, SyncGetDataMethod.INSTANCE.create(nextId()));
		}
	};

	private final Ledger ledger = new Ledger() {
		@Override
		public Result<Proof> latest() {
			return call(LedgerGetLatestProofMethod.INSTANCE, LedgerGetLatestProofMethod.INSTANCE.create(nextId()));
		}

		@Override
		public Result<Proof> epoch() {
			return call(LedgerGetLatestEpochProofMethod.INSTANCE, LedgerGetLatestEpochProofMethod.INSTANCE.create(nextId()));
		}

		@Override
		public Result<Checkpoint> checkpoints() {
			return call(CheckpointsGetCheckpointsMethod.INSTANCE, CheckpointsGetCheckpointsMethod.INSTANCE.create(nextId()));
		}
	};

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
	public SyncRadixApi withTrace() {
		enableTrace();
		return this;
	}

	@Override
	public Addressing addressing() {
		return networkAddressing();
	}

	@Override
	public SyncRadixApi withTimeout(Duration timeout) {
		setTimeout(timeout);
		return this;
	}

	private SyncRadixApi(
		String baseUrl,
		int primaryPort,
		int secondaryPort,
		HttpClient client,
		Optional<BasicAuth> authentication
	) {
		super(baseUrl, primaryPort, secondaryPort, client, authentication);
	}

	static Result<RadixApi> connect(
		String url,
		int primaryPort,
		int secondaryPort,
		Optional<BasicAuth> authentication
	) {
		return buildHttpClient().flatMap(client -> connect(url, primaryPort, secondaryPort, client, authentication));
	}

	static Result<RadixApi> connect(
		String url,
		int primaryPort,
		int secondaryPort,
		HttpClient client,
		Optional<BasicAuth> authentication
	) {
		return ofNullable(url)
			.map(baseUrl -> Result.ok(new SyncRadixApi(baseUrl, primaryPort, secondaryPort, client, authentication)))
			.orElseGet(MISSING_BASE_URL::result)
			.flatMap(syncRadixApi -> syncRadixApi.network().id()
				.onSuccess(networkId -> syncRadixApi.configureSerialization(networkId.getNetworkId()))
				.map(__ -> syncRadixApi));
	}

	private <R extends MethodParameters, T> Result<T> call(RpcMethodDescriptor<R, T> method, JsonRpcRequest<R> request) {
		return serialize(request)
			.onSuccess(this::trace)
			.map(value -> buildRequest(method, value))
			.flatMap(httpRequest -> Result.wrap(this::errorMapper, () -> client().send(httpRequest, HttpResponse.BodyHandlers.ofString())))
			.flatMap(body -> bodyHandler(body, method.getResponseType()));
	}

	private Failure errorMapper(Throwable throwable) {
		if (throwable instanceof IOException) {
			return IO_ERROR.with(throwable.getMessage());
		}

		if (throwable instanceof InterruptedException) {
			return INTERRUPTED_OPERATION.with(throwable.getMessage());
		}

		return UNKNOWN.with(throwable.getClass().getName(), throwable.getMessage());
	}

	private <T> Result<T> bodyHandler(
		HttpResponse<String> body,
		TypeReference<JsonRpcResponse<T>> reference
	) {
		return deserialize(trace(body.body()), reference)
			.flatMap(response -> response.rawError() == null
								 ? Result.ok(response.rawResult())
								 : Result.fail(response.rawError().toFailure()));
	}
}
