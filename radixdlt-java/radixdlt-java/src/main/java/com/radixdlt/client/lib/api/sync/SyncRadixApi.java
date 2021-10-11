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
import com.radixdlt.api.rpc.parameter.AccountGetBalances;
import com.radixdlt.api.rpc.parameter.AccountGetInfo;
import com.radixdlt.api.rpc.parameter.AccountGetStakePositions;
import com.radixdlt.api.rpc.parameter.AccountGetTransactionHistory;
import com.radixdlt.api.rpc.parameter.AccountGetUnstakePositions;
import com.radixdlt.api.rpc.parameter.AccountSubmitTransactionSingleStep;
import com.radixdlt.api.rpc.parameter.ApiGetConfiguration;
import com.radixdlt.api.rpc.parameter.ApiGetData;
import com.radixdlt.api.rpc.parameter.BftGetConfiguration;
import com.radixdlt.api.rpc.parameter.BftGetData;
import com.radixdlt.api.rpc.parameter.CheckpointsGetCheckpoints;
import com.radixdlt.api.rpc.parameter.ConstructionBuildTransaction;
import com.radixdlt.api.rpc.parameter.ConstructionFinalizeTransaction;
import com.radixdlt.api.rpc.parameter.ConstructionSubmitTransaction;
import com.radixdlt.api.rpc.parameter.GetTransactions;
import com.radixdlt.api.rpc.parameter.LedgerGetLatestEpochProof;
import com.radixdlt.api.rpc.parameter.LedgerGetLatestProof;
import com.radixdlt.api.rpc.parameter.MempoolGetConfiguration;
import com.radixdlt.api.rpc.parameter.MempoolGetData;
import com.radixdlt.api.rpc.parameter.NetworkGetDemand;
import com.radixdlt.api.rpc.parameter.NetworkGetId;
import com.radixdlt.api.rpc.parameter.NetworkGetThroughput;
import com.radixdlt.api.rpc.parameter.NetworkingGetAddressBook;
import com.radixdlt.api.rpc.parameter.NetworkingGetConfiguration;
import com.radixdlt.api.rpc.parameter.NetworkingGetData;
import com.radixdlt.api.rpc.parameter.NetworkingGetPeers;
import com.radixdlt.api.rpc.parameter.RadixEngineGetConfiguration;
import com.radixdlt.api.rpc.parameter.RadixEngineGetData;
import com.radixdlt.api.rpc.parameter.SyncGetConfiguration;
import com.radixdlt.api.rpc.parameter.SyncGetData;
import com.radixdlt.api.rpc.parameter.TokensGetInfo;
import com.radixdlt.api.rpc.parameter.TokensGetNativeToken;
import com.radixdlt.api.rpc.parameter.TransactionsGetTransactionStatus;
import com.radixdlt.api.rpc.parameter.TransactionsLookupTransaction;
import com.radixdlt.api.rpc.parameter.ValidationGetCurrentEpochData;
import com.radixdlt.api.rpc.parameter.ValidationGetNodeInfo;
import com.radixdlt.api.rpc.parameter.ValidatorsGetNextEpochSet;
import com.radixdlt.api.rpc.parameter.ValidatorsLookupValidator;
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

import static com.radixdlt.api.rpc.RpcMethod.ACCOUNT_BALANCES;
import static com.radixdlt.api.rpc.RpcMethod.ACCOUNT_HISTORY;
import static com.radixdlt.api.rpc.RpcMethod.ACCOUNT_INFO;
import static com.radixdlt.api.rpc.RpcMethod.ACCOUNT_STAKES;
import static com.radixdlt.api.rpc.RpcMethod.ACCOUNT_SUBMIT_SINGLE_STEP;
import static com.radixdlt.api.rpc.RpcMethod.ACCOUNT_UNSTAKES;
import static com.radixdlt.api.rpc.RpcMethod.API_CONFIGURATION;
import static com.radixdlt.api.rpc.RpcMethod.API_DATA;
import static com.radixdlt.api.rpc.RpcMethod.BFT_CONFIGURATION;
import static com.radixdlt.api.rpc.RpcMethod.BFT_DATA;
import static com.radixdlt.api.rpc.RpcMethod.CONSTRUCTION_BUILD;
import static com.radixdlt.api.rpc.RpcMethod.CONSTRUCTION_FINALIZE;
import static com.radixdlt.api.rpc.RpcMethod.CONSTRUCTION_SUBMIT;
import static com.radixdlt.api.rpc.RpcMethod.LEDGER_CHECKPOINTS;
import static com.radixdlt.api.rpc.RpcMethod.LEDGER_EPOCH_PROOF;
import static com.radixdlt.api.rpc.RpcMethod.LEDGER_PROOF;
import static com.radixdlt.api.rpc.RpcMethod.MEMPOOL_CONFIGURATION;
import static com.radixdlt.api.rpc.RpcMethod.MEMPOOL_DATA;
import static com.radixdlt.api.rpc.RpcMethod.NETWORK_ADDRESS_BOOK;
import static com.radixdlt.api.rpc.RpcMethod.NETWORK_CONFIG;
import static com.radixdlt.api.rpc.RpcMethod.NETWORK_DATA;
import static com.radixdlt.api.rpc.RpcMethod.NETWORK_DEMAND;
import static com.radixdlt.api.rpc.RpcMethod.NETWORK_ID;
import static com.radixdlt.api.rpc.RpcMethod.NETWORK_PEERS;
import static com.radixdlt.api.rpc.RpcMethod.NETWORK_THROUGHPUT;
import static com.radixdlt.api.rpc.RpcMethod.RADIX_ENGINE_CONFIGURATION;
import static com.radixdlt.api.rpc.RpcMethod.RADIX_ENGINE_DATA;
import static com.radixdlt.api.rpc.RpcMethod.SYNC_CONFIGURATION;
import static com.radixdlt.api.rpc.RpcMethod.SYNC_DATA;
import static com.radixdlt.api.rpc.RpcMethod.TOKEN_INFO;
import static com.radixdlt.api.rpc.RpcMethod.TOKEN_NATIVE;
import static com.radixdlt.api.rpc.RpcMethod.TRANSACTION_LIST;
import static com.radixdlt.api.rpc.RpcMethod.TRANSACTION_LOOKUP;
import static com.radixdlt.api.rpc.RpcMethod.TRANSACTION_STATUS;
import static com.radixdlt.api.rpc.RpcMethod.VALIDATION_CURRENT_EPOCH;
import static com.radixdlt.api.rpc.RpcMethod.VALIDATION_NODE_INFO;
import static com.radixdlt.api.rpc.RpcMethod.VALIDATORS_LIST;
import static com.radixdlt.api.rpc.RpcMethod.VALIDATORS_LOOKUP;
import static com.radixdlt.errors.ClientErrors.INTERRUPTED_OPERATION;
import static com.radixdlt.errors.ClientErrors.IO_ERROR;
import static com.radixdlt.errors.ClientErrors.MISSING_BASE_URL;
import static com.radixdlt.errors.InternalErrors.UNKNOWN;

import static java.util.Optional.ofNullable;

public class SyncRadixApi extends RadixApiBase implements RadixApi {
	private final Network network = new Network() {
		@Override
		public Result<NetworkId> id() {
			return call(request(NETWORK_ID, NetworkGetId.INSTANCE), new TypeReference<>() {});
		}

		@Override
		public Result<NetworkStats> throughput() {
			return call(request(NETWORK_THROUGHPUT, NetworkGetThroughput.INSTANCE), new TypeReference<>() {});
		}

		@Override
		public Result<NetworkStats> demand() {
			return call(request(NETWORK_DEMAND, NetworkGetDemand.INSTANCE), new TypeReference<>() {});
		}

		@Override
		public Result<NetworkConfiguration> configuration() {
			return call(request(NETWORK_CONFIG, NetworkingGetConfiguration.INSTANCE), new TypeReference<>() {});
		}

		@Override
		public Result<NetworkData> data() {
			return call(request(NETWORK_DATA, NetworkingGetData.INSTANCE), new TypeReference<>() {});
		}

		@Override
		public Result<List<NetworkPeer>> peers() {
			return call(request(NETWORK_PEERS, NetworkingGetPeers.INSTANCE), new TypeReference<>() {});
		}

		@Override
		public Result<List<AddressBookEntry>> addressBook() {
			return call(request(NETWORK_ADDRESS_BOOK, NetworkingGetAddressBook.INSTANCE), new TypeReference<>() {});
		}
	};

	private final Token token = new Token() {
		@Override
		public Result<TokenInfo> describeNative() {
			return call(request(TOKEN_NATIVE, TokensGetNativeToken.INSTANCE), new TypeReference<>() {});
		}

		@Override
		public Result<TokenInfo> describe(String rri) {
			return call(request(TOKEN_INFO, TokensGetInfo.create(rri)), new TypeReference<>() {});
		}
	};

	private final Transaction transaction = new Transaction() {
		@Override
		public Result<BuiltTransaction> build(TransactionRequest request) {
			return call(request(CONSTRUCTION_BUILD, ConstructionBuildTransaction.from(request)), new TypeReference<>() {});
		}

		@Override
		public Result<TxBlobDTO> finalize(FinalizedTransaction request, boolean immediateSubmit) {
			return call(request(CONSTRUCTION_FINALIZE, ConstructionFinalizeTransaction.from(request, immediateSubmit)), new TypeReference<>() {});
		}

		@Override
		public Result<TxDTO> submit(TxBlobDTO request) {
			return call(request(CONSTRUCTION_SUBMIT, ConstructionSubmitTransaction.from(request)), new TypeReference<>() {});
		}

		@Override
		public Result<TransactionDTO> lookup(AID txId) {
			return call(request(TRANSACTION_LOOKUP, TransactionsLookupTransaction.create(txId)), new TypeReference<>() {});
		}

		@Override
		public Result<TransactionStatusDTO> status(AID txId) {
			return call(request(TRANSACTION_STATUS, TransactionsGetTransactionStatus.create(txId)), new TypeReference<>() {});
		}

		@Override
		public Result<TransactionsDTO> list(long limit, OptionalLong offset) {
			return call(request(TRANSACTION_LIST, GetTransactions.from(limit, offset)), new TypeReference<>() {});
		}
	};

	private final SingleAccount account = new SingleAccount() {
		@Override
		public Result<TokenBalances> balances(AccountAddress address) {
			return call(request(ACCOUNT_BALANCES, AccountGetBalances.create(address)), new TypeReference<>() {});
		}

		@Override
		public Result<TransactionHistory> history(
			AccountAddress address, int size, OptionalLong nextOffset, boolean verbose
		) {
			return call(request(ACCOUNT_HISTORY, AccountGetTransactionHistory.from(address, size, nextOffset, verbose)), new TypeReference<>() {});
		}

		@Override
		public Result<List<StakePositions>> stakes(AccountAddress address) {
			return call(request(ACCOUNT_STAKES, AccountGetStakePositions.create(address)), new TypeReference<>() {});
		}

		@Override
		public Result<List<UnstakePositions>> unstakes(AccountAddress address) {
			return call(request(ACCOUNT_UNSTAKES, AccountGetUnstakePositions.create(address)), new TypeReference<>() {});
		}
	};

	private final Validator validator = new Validator() {
		@Override
		public Result<ValidatorsResponse> list(long size, Optional<NavigationCursor> cursor) {
			return call(request(VALIDATORS_LIST, ValidatorsGetNextEpochSet.create(size, cursor)), new TypeReference<>() {});
		}

		@Override
		public Result<ValidatorDTO> lookup(ValidatorAddress validatorAddress) {
			return call(request(VALIDATORS_LOOKUP, ValidatorsLookupValidator.create(validatorAddress)), new TypeReference<>() {});
		}
	};

	private final Local local = new Local() {
		@Override
		public Result<LocalAccount> accountInfo() {
			return call(request(ACCOUNT_INFO, AccountGetInfo.INSTANCE), new TypeReference<>() {});
		}

		@Override
		public Result<TxDTO> submitTxSingleStep(TransactionRequest request) {
			return call(request(ACCOUNT_SUBMIT_SINGLE_STEP, AccountSubmitTransactionSingleStep.from(request)), new TypeReference<>() {});
		}

		@Override
		public Result<LocalValidatorInfo> validatorInfo() {
			return call(request(VALIDATION_NODE_INFO, ValidationGetNodeInfo.INSTANCE), new TypeReference<>() {});
		}

		@Override
		public Result<EpochData> currentEpoch() {
			return call(request(VALIDATION_CURRENT_EPOCH, ValidationGetCurrentEpochData.INSTANCE), new TypeReference<>() {});
		}
	};

	private final Api api = new Api() {
		@Override
		public Result<ApiConfiguration> configuration() {
			return call(request(API_CONFIGURATION, ApiGetConfiguration.INSTANCE), new TypeReference<>() {});
		}

		@Override
		public Result<ApiData> data() {
			return call(request(API_DATA, ApiGetData.INSTANCE), new TypeReference<>() {});
		}
	};

	private final Consensus consensus = new Consensus() {
		@Override
		public Result<ConsensusConfiguration> configuration() {
			return call(request(BFT_CONFIGURATION, BftGetConfiguration.INSTANCE), new TypeReference<>() {});
		}

		@Override
		public Result<ConsensusData> data() {
			return call(request(BFT_DATA, BftGetData.INSTANCE), new TypeReference<>() {});
		}
	};

	private final Mempool mempool = new Mempool() {
		@Override
		public Result<MempoolConfiguration> configuration() {
			return call(request(MEMPOOL_CONFIGURATION, MempoolGetConfiguration.INSTANCE), new TypeReference<>() {});
		}

		@Override
		public Result<MempoolData> data() {
			return call(request(MEMPOOL_DATA, MempoolGetData.INSTANCE), new TypeReference<>() {});
		}
	};

	private final RadixEngine radixEngine = new RadixEngine() {
		@Override
		public Result<List<ForkDetails>> configuration() {
			return call(request(RADIX_ENGINE_CONFIGURATION, RadixEngineGetConfiguration.INSTANCE), new TypeReference<>() {});
		}

		@Override
		public Result<RadixEngineData> data() {
			return call(request(RADIX_ENGINE_DATA, RadixEngineGetData.INSTANCE), new TypeReference<>() {});
		}
	};

	private final Sync sync = new Sync() {
		@Override
		public Result<SyncConfiguration> configuration() {
			return call(request(SYNC_CONFIGURATION, SyncGetConfiguration.INSTANCE), new TypeReference<>() {});
		}

		@Override
		public Result<SyncData> data() {
			return call(request(SYNC_DATA, SyncGetData.INSTANCE), new TypeReference<>() {});
		}
	};

	private final Ledger ledger = new Ledger() {
		@Override
		public Result<Proof> latest() {
			return call(request(LEDGER_PROOF, LedgerGetLatestProof.INSTANCE), new TypeReference<>() {});
		}

		@Override
		public Result<Proof> epoch() {
			return call(request(LEDGER_EPOCH_PROOF, LedgerGetLatestEpochProof.INSTANCE), new TypeReference<>() {});
		}

		@Override
		public Result<Checkpoint> checkpoints() {
			return call(request(LEDGER_CHECKPOINTS, CheckpointsGetCheckpoints.INSTANCE), new TypeReference<>() {});
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

	private <T> Result<T> call(JsonRpcRequest<?> request, TypeReference<JsonRpcResponse<T>> typeReference) {
		return serialize(request)
			.onSuccess(this::trace)
			.map(value -> buildRequest(request, value))
			.flatMap(httpRequest -> Result.wrap(this::errorMapper, () -> client().send(httpRequest, HttpResponse.BodyHandlers.ofString())))
			.flatMap(body -> bodyHandler(body, typeReference));
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
