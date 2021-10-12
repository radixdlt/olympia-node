package com.radixdlt.api.rpc;

import com.fasterxml.jackson.core.type.TypeReference;
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
import com.radixdlt.api.rpc.parameter.AccountGetBalancesRequest;
import com.radixdlt.api.rpc.parameter.AccountGetInfoRequest;
import com.radixdlt.api.rpc.parameter.AccountGetStakePositionsRequest;
import com.radixdlt.api.rpc.parameter.AccountGetTransactionHistoryRequest;
import com.radixdlt.api.rpc.parameter.AccountGetUnstakePositionsRequest;
import com.radixdlt.api.rpc.parameter.AccountSubmitTransactionSingleStepRequest;
import com.radixdlt.api.rpc.parameter.ApiGetConfigurationRequest;
import com.radixdlt.api.rpc.parameter.ApiGetDataRequest;
import com.radixdlt.api.rpc.parameter.BftGetConfigurationRequest;
import com.radixdlt.api.rpc.parameter.BftGetDataRequest;
import com.radixdlt.api.rpc.parameter.CheckpointsGetCheckpointsRequest;
import com.radixdlt.api.rpc.parameter.ConstructionBuildTransactionRequest;
import com.radixdlt.api.rpc.parameter.ConstructionFinalizeTransactionRequest;
import com.radixdlt.api.rpc.parameter.ConstructionSubmitTransactionRequest;
import com.radixdlt.api.rpc.parameter.GetTransactionsRequest;
import com.radixdlt.api.rpc.parameter.LedgerGetLatestEpochProofRequest;
import com.radixdlt.api.rpc.parameter.LedgerGetLatestProofRequest;
import com.radixdlt.api.rpc.parameter.MempoolGetConfigurationRequest;
import com.radixdlt.api.rpc.parameter.MempoolGetDataRequest;
import com.radixdlt.api.rpc.parameter.NetworkGetDemandRequest;
import com.radixdlt.api.rpc.parameter.NetworkGetIdRequest;
import com.radixdlt.api.rpc.parameter.NetworkGetThroughputRequest;
import com.radixdlt.api.rpc.parameter.NetworkingGetAddressBookRequest;
import com.radixdlt.api.rpc.parameter.NetworkingGetConfigurationRequest;
import com.radixdlt.api.rpc.parameter.NetworkingGetDataRequest;
import com.radixdlt.api.rpc.parameter.NetworkingGetPeersRequest;
import com.radixdlt.api.rpc.parameter.RadixEngineGetConfigurationRequest;
import com.radixdlt.api.rpc.parameter.RadixEngineGetDataRequest;
import com.radixdlt.api.rpc.parameter.SyncGetConfigurationRequest;
import com.radixdlt.api.rpc.parameter.SyncGetDataRequest;
import com.radixdlt.api.rpc.parameter.TokensGetInfoRequest;
import com.radixdlt.api.rpc.parameter.TokensGetNativeTokenRequest;
import com.radixdlt.api.rpc.parameter.TransactionsGetTransactionStatusRequest;
import com.radixdlt.api.rpc.parameter.TransactionsLookupTransactionRequest;
import com.radixdlt.api.rpc.parameter.ValidationGetCurrentEpochDataRequest;
import com.radixdlt.api.rpc.parameter.ValidationGetNodeInfoRequest;
import com.radixdlt.api.rpc.parameter.ValidatorsGetNextEpochSetRequest;
import com.radixdlt.api.rpc.parameter.ValidatorsLookupValidatorRequest;
import com.radixdlt.api.types.AccountAddress;
import com.radixdlt.api.types.NavigationCursor;
import com.radixdlt.api.types.TransactionRequest;
import com.radixdlt.api.types.ValidatorAddress;
import com.radixdlt.identifiers.AID;

import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

import static com.radixdlt.api.rpc.EndPoint.ACCOUNT;
import static com.radixdlt.api.rpc.EndPoint.ARCHIVE;
import static com.radixdlt.api.rpc.EndPoint.CONSTRUCTION;
import static com.radixdlt.api.rpc.EndPoint.SYSTEM;
import static com.radixdlt.api.rpc.EndPoint.TRANSACTIONS;
import static com.radixdlt.api.rpc.EndPoint.VALIDATION;

public sealed class RpcMethodDescriptor<Request, Response> {
	private final Class<Request> requestType;
	private final TypeReference<JsonRpcResponse<Response>> responseType;
	private final String method;
	private final EndPoint endPoint;

	protected RpcMethodDescriptor(
		Class<Request> requestType,
		TypeReference<JsonRpcResponse<Response>> responseType,
		String method,
		EndPoint endPoint
	) {
		this.requestType = requestType;
		this.responseType = responseType;
		this.method = method;
		this.endPoint = endPoint;
	}

	public String method() {
		return method;
	}

	public EndPoint endPoint() {
		return endPoint;
	}

	public Class<Request> getRequestType() {
		return requestType;
	}

	public TypeReference<JsonRpcResponse<Response>> getResponseType() {
		return responseType;
	}

	public static final class AccountGetBalancesMethod extends RpcMethodDescriptor<AccountGetBalancesRequest, TokenBalances> {
		public static final String METHOD_NAME = "account.get_balances";
		public static final AccountGetBalancesMethod INSTANCE = new AccountGetBalancesMethod();

		private AccountGetBalancesMethod() {
			super(AccountGetBalancesRequest.class, new TypeReference<>() {}, METHOD_NAME, ARCHIVE);
		}

		public JsonRpcRequest<AccountGetBalancesRequest> create(long id, AccountAddress address) {
			return JsonRpcRequest.create(method(), id, AccountGetBalancesRequest.create(address));
		}
	}

	public static final class AccountGetTransactionHistoryMethod
		extends RpcMethodDescriptor<AccountGetTransactionHistoryRequest, TransactionHistory> {
		public static final String METHOD_NAME = "account.get_transaction_history";
		public static final AccountGetTransactionHistoryMethod INSTANCE = new AccountGetTransactionHistoryMethod();

		private AccountGetTransactionHistoryMethod() {
			super(AccountGetTransactionHistoryRequest.class, new TypeReference<>() {}, METHOD_NAME, ARCHIVE);
		}

		public JsonRpcRequest<AccountGetTransactionHistoryRequest> create(
			long id,
			AccountAddress address,
			long size,
			OptionalLong nextOffset,
			boolean verbose
		) {
			return JsonRpcRequest.create(method(), id, AccountGetTransactionHistoryRequest.from(address, size, nextOffset, verbose));
		}
	}

	public static final class AccountGetInfoMethod extends RpcMethodDescriptor<AccountGetInfoRequest, LocalAccount> {
		public static final String METHOD_NAME = "account.get_info";
		public static final AccountGetInfoMethod INSTANCE = new AccountGetInfoMethod();

		private AccountGetInfoMethod() {
			super(AccountGetInfoRequest.class, new TypeReference<>() {}, METHOD_NAME, ACCOUNT);
		}

		public JsonRpcRequest<AccountGetInfoRequest> create(long id) {
			return JsonRpcRequest.create(method(), id, AccountGetInfoRequest.create());
		}
	}

	public static final class AccountGetStakePositionsMethod extends RpcMethodDescriptor<AccountGetStakePositionsRequest, List<StakePositions>> {
		public static final String METHOD_NAME = "account.get_stake_positions";
		public static final AccountGetStakePositionsMethod INSTANCE = new AccountGetStakePositionsMethod();

		private AccountGetStakePositionsMethod() {
			super(AccountGetStakePositionsRequest.class, new TypeReference<>() {}, METHOD_NAME, ARCHIVE);
		}

		public JsonRpcRequest<AccountGetStakePositionsRequest> create(long id, AccountAddress address) {
			return JsonRpcRequest.create(method(), id, AccountGetStakePositionsRequest.create(address));
		}
	}

	public static final class AccountSubmitTransactionSingleStepMethod extends RpcMethodDescriptor<AccountSubmitTransactionSingleStepRequest, TxDTO> {
		public static final String METHOD_NAME = "account.submit_transaction_single_step";
		public static final AccountSubmitTransactionSingleStepMethod INSTANCE = new AccountSubmitTransactionSingleStepMethod();

		private AccountSubmitTransactionSingleStepMethod() {
			super(AccountSubmitTransactionSingleStepRequest.class, new TypeReference<>() {}, METHOD_NAME, ACCOUNT);
		}

		public JsonRpcRequest<AccountSubmitTransactionSingleStepRequest> create(long id, TransactionRequest request) {
			return JsonRpcRequest.create(method(), id, AccountSubmitTransactionSingleStepRequest.from(request));
		}
	}

	public static final class AccountGetUnstakePositionsMethod
		extends RpcMethodDescriptor<AccountGetUnstakePositionsRequest, List<UnstakePositions>> {
		public static final String METHOD_NAME = "account.get_unstake_positions";
		public static final AccountGetUnstakePositionsMethod INSTANCE = new AccountGetUnstakePositionsMethod();

		private AccountGetUnstakePositionsMethod() {
			super(AccountGetUnstakePositionsRequest.class, new TypeReference<>() {}, METHOD_NAME, ARCHIVE);
		}

		public JsonRpcRequest<AccountGetUnstakePositionsRequest> create(long id, AccountAddress address) {
			return JsonRpcRequest.create(method(), id, AccountGetUnstakePositionsRequest.create(address));
		}
	}

	public static final class ApiGetConfigurationMethod extends RpcMethodDescriptor<ApiGetConfigurationRequest, ApiConfiguration> {
		public static final String METHOD_NAME = "api.get_configuration";
		public static final ApiGetConfigurationMethod INSTANCE = new ApiGetConfigurationMethod();

		private ApiGetConfigurationMethod() {
			super(ApiGetConfigurationRequest.class, new TypeReference<>() {}, METHOD_NAME, SYSTEM);
		}

		public JsonRpcRequest<ApiGetConfigurationRequest> create(long id) {
			return JsonRpcRequest.create(method(), id, ApiGetConfigurationRequest.create());
		}
	}

	public static final class ApiGetDataMethod extends RpcMethodDescriptor<ApiGetDataRequest, ApiData> {
		public static final String METHOD_NAME = "api.get_data";
		public static final ApiGetDataMethod INSTANCE = new ApiGetDataMethod();

		private ApiGetDataMethod() {
			super(ApiGetDataRequest.class, new TypeReference<>() {}, METHOD_NAME, SYSTEM);
		}

		public JsonRpcRequest<ApiGetDataRequest> create(long id) {
			return JsonRpcRequest.create(method(), id, ApiGetDataRequest.create());
		}
	}

	public static final class BftGetConfigurationMethod extends RpcMethodDescriptor<BftGetConfigurationRequest, ConsensusConfiguration> {
		public static final String METHOD_NAME = "bft.get_configuration";
		public static final BftGetConfigurationMethod INSTANCE = new BftGetConfigurationMethod();

		private BftGetConfigurationMethod() {
			super(BftGetConfigurationRequest.class, new TypeReference<>() {}, METHOD_NAME, SYSTEM);
		}

		public JsonRpcRequest<BftGetConfigurationRequest> create(long id) {
			return JsonRpcRequest.create(method(), id, BftGetConfigurationRequest.create());
		}
	}

	public static final class BftGetDataMethod extends RpcMethodDescriptor<BftGetDataRequest, ConsensusData> {
		public static final String METHOD_NAME = "bft.get_data";
		public static final BftGetDataMethod INSTANCE = new BftGetDataMethod();

		private BftGetDataMethod() {
			super(BftGetDataRequest.class, new TypeReference<>() {}, METHOD_NAME, SYSTEM);
		}

		public JsonRpcRequest<BftGetDataRequest> create(long id) {
			return JsonRpcRequest.create(method(), id, BftGetDataRequest.create());
		}
	}

	public static final class ConstructionBuildTransactionMethod extends RpcMethodDescriptor<ConstructionBuildTransactionRequest, BuiltTransaction> {
		public static final String METHOD_NAME = "construction.build_transaction";
		public static final ConstructionBuildTransactionMethod INSTANCE = new ConstructionBuildTransactionMethod();

		private ConstructionBuildTransactionMethod() {
			super(ConstructionBuildTransactionRequest.class, new TypeReference<>() {}, METHOD_NAME, CONSTRUCTION);
		}

		public JsonRpcRequest<ConstructionBuildTransactionRequest> create(long id, TransactionRequest request) {
			return JsonRpcRequest.create(method(), id, ConstructionBuildTransactionRequest.from(request));
		}
	}

	public static final class ConstructionFinalizeTransactionMethod extends RpcMethodDescriptor<ConstructionFinalizeTransactionRequest, TxBlobDTO> {
		public static final String METHOD_NAME = "construction.finalize_transaction";
		public static final ConstructionFinalizeTransactionMethod INSTANCE = new ConstructionFinalizeTransactionMethod();

		private ConstructionFinalizeTransactionMethod() {
			super(ConstructionFinalizeTransactionRequest.class, new TypeReference<>() {}, METHOD_NAME, CONSTRUCTION);
		}

		public JsonRpcRequest<ConstructionFinalizeTransactionRequest> create(long id, FinalizedTransaction request, boolean immediateSubmit) {
			return JsonRpcRequest.create(method(), id, ConstructionFinalizeTransactionRequest.from(request, immediateSubmit));
		}
	}

	public static final class ConstructionSubmitTransactionMethod extends RpcMethodDescriptor<ConstructionSubmitTransactionRequest, TxDTO> {
		public static final String METHOD_NAME = "construction.submit_transaction";
		public static final ConstructionSubmitTransactionMethod INSTANCE = new ConstructionSubmitTransactionMethod();

		private ConstructionSubmitTransactionMethod() {
			super(ConstructionSubmitTransactionRequest.class, new TypeReference<>() {}, METHOD_NAME, CONSTRUCTION);
		}

		public JsonRpcRequest<ConstructionSubmitTransactionRequest> create(long id, TxBlobDTO request) {
			return JsonRpcRequest.create(method(), id, ConstructionSubmitTransactionRequest.from(request));
		}
	}

	public static final class CheckpointsGetCheckpointsMethod extends RpcMethodDescriptor<CheckpointsGetCheckpointsRequest, Checkpoint> {
		public static final String METHOD_NAME = "checkpoints.get_checkpoints";
		public static final CheckpointsGetCheckpointsMethod INSTANCE = new CheckpointsGetCheckpointsMethod();

		private CheckpointsGetCheckpointsMethod() {
			super(CheckpointsGetCheckpointsRequest.class, new TypeReference<>() {}, METHOD_NAME, SYSTEM);
		}

		public JsonRpcRequest<CheckpointsGetCheckpointsRequest> create(long id) {
			return JsonRpcRequest.create(method(), id, CheckpointsGetCheckpointsRequest.create());
		}
	}

	public static final class LedgerGetLatestEpochProofMethod extends RpcMethodDescriptor<LedgerGetLatestEpochProofRequest, Proof> {
		public static final String METHOD_NAME = "ledger.get_latest_epoch_proof";
		public static final LedgerGetLatestEpochProofMethod INSTANCE = new LedgerGetLatestEpochProofMethod();

		private LedgerGetLatestEpochProofMethod() {
			super(LedgerGetLatestEpochProofRequest.class, new TypeReference<>() {}, METHOD_NAME, SYSTEM);
		}

		public JsonRpcRequest<LedgerGetLatestEpochProofRequest> create(long id) {
			return JsonRpcRequest.create(method(), id, LedgerGetLatestEpochProofRequest.create());
		}
	}

	public static final class LedgerGetLatestProofMethod extends RpcMethodDescriptor<LedgerGetLatestProofRequest, Proof> {
		public static final String METHOD_NAME = "ledger.get_latest_proof";
		public static final LedgerGetLatestProofMethod INSTANCE = new LedgerGetLatestProofMethod();

		private LedgerGetLatestProofMethod() {
			super(LedgerGetLatestProofRequest.class, new TypeReference<>() {}, METHOD_NAME, SYSTEM);
		}

		public JsonRpcRequest<LedgerGetLatestProofRequest> create(long id) {
			return JsonRpcRequest.create(method(), id, LedgerGetLatestProofRequest.create());
		}
	}

	public static final class MempoolGetConfigurationMethod extends RpcMethodDescriptor<MempoolGetConfigurationRequest, MempoolConfiguration> {
		public static final String METHOD_NAME = "mempool.get_configuration";
		public static final MempoolGetConfigurationMethod INSTANCE = new MempoolGetConfigurationMethod();

		private MempoolGetConfigurationMethod() {
			super(MempoolGetConfigurationRequest.class, new TypeReference<>() {}, METHOD_NAME, SYSTEM);
		}

		public JsonRpcRequest<MempoolGetConfigurationRequest> create(long id) {
			return JsonRpcRequest.create(method(), id, MempoolGetConfigurationRequest.create());
		}
	}

	public static final class MempoolGetDataMethod extends RpcMethodDescriptor<MempoolGetDataRequest, MempoolData> {
		public static final String METHOD_NAME = "mempool.get_data";
		public static final MempoolGetDataMethod INSTANCE = new MempoolGetDataMethod();

		private MempoolGetDataMethod() {
			super(MempoolGetDataRequest.class, new TypeReference<>() {}, METHOD_NAME, SYSTEM);
		}

		public JsonRpcRequest<MempoolGetDataRequest> create(long id) {
			return JsonRpcRequest.create(method(), id, MempoolGetDataRequest.create());
		}
	}

	public static final class NetworkingGetAddressBookMethod extends RpcMethodDescriptor<NetworkingGetAddressBookRequest, List<AddressBookEntry>> {
		public static final String METHOD_NAME = "networking.get_address_book";
		public static final NetworkingGetAddressBookMethod INSTANCE = new NetworkingGetAddressBookMethod();

		private NetworkingGetAddressBookMethod() {
			super(NetworkingGetAddressBookRequest.class, new TypeReference<>() {}, METHOD_NAME, SYSTEM);
		}

		public JsonRpcRequest<NetworkingGetAddressBookRequest> create(long id) {
			return JsonRpcRequest.create(method(), id, NetworkingGetAddressBookRequest.create());
		}
	}

	public static final class NetworkingGetConfigurationMethod extends RpcMethodDescriptor<NetworkingGetConfigurationRequest, NetworkConfiguration> {
		public static final String METHOD_NAME = "networking.get_configuration";
		public static final NetworkingGetConfigurationMethod INSTANCE = new NetworkingGetConfigurationMethod();

		private NetworkingGetConfigurationMethod() {
			super(NetworkingGetConfigurationRequest.class, new TypeReference<>() {}, METHOD_NAME, SYSTEM);
		}

		public JsonRpcRequest<NetworkingGetConfigurationRequest> create(long id) {
			return JsonRpcRequest.create(method(), id, NetworkingGetConfigurationRequest.create());
		}
	}

	public static final class NetworkingGetDataMethod extends RpcMethodDescriptor<NetworkingGetDataRequest, NetworkData> {
		public static final String METHOD_NAME = "networking.get_data";
		public static final NetworkingGetDataMethod INSTANCE = new NetworkingGetDataMethod();

		private NetworkingGetDataMethod() {
			super(NetworkingGetDataRequest.class, new TypeReference<>() {}, METHOD_NAME, SYSTEM);
		}

		public JsonRpcRequest<NetworkingGetDataRequest> create(long id) {
			return JsonRpcRequest.create(method(), id, NetworkingGetDataRequest.create());
		}
	}

	public static final class NetworkGetDemandMethod extends RpcMethodDescriptor<NetworkGetDemandRequest, NetworkStats> {
		public static final String METHOD_NAME = "network.get_demand";
		public static final NetworkGetDemandMethod INSTANCE = new NetworkGetDemandMethod();

		private NetworkGetDemandMethod() {
			super(NetworkGetDemandRequest.class, new TypeReference<>() {}, METHOD_NAME, ARCHIVE);
		}

		public JsonRpcRequest<NetworkGetDemandRequest> create(long id) {
			return JsonRpcRequest.create(method(), id, NetworkGetDemandRequest.create());
		}
	}

	public static final class NetworkGetIdMethod extends RpcMethodDescriptor<NetworkGetIdRequest, NetworkId> {
		public static final String METHOD_NAME = "network.get_id";
		public static final NetworkGetIdMethod INSTANCE = new NetworkGetIdMethod();

		private NetworkGetIdMethod() {
			super(NetworkGetIdRequest.class, new TypeReference<>() {}, METHOD_NAME, ARCHIVE);
		}

		public JsonRpcRequest<NetworkGetIdRequest> create(long id) {
			return JsonRpcRequest.create(method(), id, NetworkGetIdRequest.create());
		}
	}

	public static final class NetworkingGetPeersMethod extends RpcMethodDescriptor<NetworkingGetPeersRequest, List<NetworkPeer>> {
		public static final String METHOD_NAME = "networking.get_peers";
		public static final NetworkingGetPeersMethod INSTANCE = new NetworkingGetPeersMethod();

		private NetworkingGetPeersMethod() {
			super(NetworkingGetPeersRequest.class, new TypeReference<>() {}, METHOD_NAME, SYSTEM);
		}

		public JsonRpcRequest<NetworkingGetPeersRequest> create(long id) {
			return JsonRpcRequest.create(method(), id, NetworkingGetPeersRequest.create());
		}
	}

	public static final class NetworkGetThroughputMethod extends RpcMethodDescriptor<NetworkGetThroughputRequest, NetworkStats> {
		public static final String METHOD_NAME = "network.get_throughput";
		public static final NetworkGetThroughputMethod INSTANCE = new NetworkGetThroughputMethod();

		private NetworkGetThroughputMethod() {
			super(NetworkGetThroughputRequest.class, new TypeReference<>() {}, METHOD_NAME, ARCHIVE);
		}

		public JsonRpcRequest<NetworkGetThroughputRequest> create(long id) {
			return JsonRpcRequest.create(method(), id, NetworkGetThroughputRequest.create());
		}
	}

	public static final class RadixEngineGetConfigurationMethod extends RpcMethodDescriptor<RadixEngineGetConfigurationRequest, List<ForkDetails>> {
		public static final String METHOD_NAME = "radix_engine.get_configuration";
		public static final RadixEngineGetConfigurationMethod INSTANCE = new RadixEngineGetConfigurationMethod();

		private RadixEngineGetConfigurationMethod() {
			super(RadixEngineGetConfigurationRequest.class, new TypeReference<>() {}, METHOD_NAME, SYSTEM);
		}

		public JsonRpcRequest<RadixEngineGetConfigurationRequest> create(long id) {
			return JsonRpcRequest.create(method(), id, RadixEngineGetConfigurationRequest.create());
		}
	}

	public static final class RadixEngineGetDataMethod extends RpcMethodDescriptor<RadixEngineGetDataRequest, RadixEngineData> {
		public static final String METHOD_NAME = "radix_engine.get_data";
		public static final RadixEngineGetDataMethod INSTANCE = new RadixEngineGetDataMethod();

		private RadixEngineGetDataMethod() {
			super(RadixEngineGetDataRequest.class, new TypeReference<>() {}, METHOD_NAME, SYSTEM);
		}

		public JsonRpcRequest<RadixEngineGetDataRequest> create(long id) {
			return JsonRpcRequest.create(method(), id, RadixEngineGetDataRequest.create());
		}
	}

	public static final class SyncGetConfigurationMethod extends RpcMethodDescriptor<SyncGetConfigurationRequest, SyncConfiguration> {
		public static final String METHOD_NAME = "sync.get_configuration";
		public static final SyncGetConfigurationMethod INSTANCE = new SyncGetConfigurationMethod();

		private SyncGetConfigurationMethod() {
			super(SyncGetConfigurationRequest.class, new TypeReference<>() {}, METHOD_NAME, SYSTEM);
		}

		public JsonRpcRequest<SyncGetConfigurationRequest> create(long id) {
			return JsonRpcRequest.create(method(), id, SyncGetConfigurationRequest.create());
		}
	}

	public static final class SyncGetDataMethod extends RpcMethodDescriptor<SyncGetDataRequest, SyncData> {
		public static final String METHOD_NAME = "sync.get_data";
		public static final SyncGetDataMethod INSTANCE = new SyncGetDataMethod();

		private SyncGetDataMethod() {
			super(SyncGetDataRequest.class, new TypeReference<>() {}, METHOD_NAME, SYSTEM);
		}

		public JsonRpcRequest<SyncGetDataRequest> create(long id) {
			return JsonRpcRequest.create(method(), id, SyncGetDataRequest.create());
		}
	}

	public static final class TokensGetInfoMethod extends RpcMethodDescriptor<TokensGetInfoRequest, TokenInfo> {
		public static final String METHOD_NAME = "tokens.get_info";
		public static final TokensGetInfoMethod INSTANCE = new TokensGetInfoMethod();

		private TokensGetInfoMethod() {
			super(TokensGetInfoRequest.class, new TypeReference<>() {}, METHOD_NAME, ARCHIVE);
		}

		public JsonRpcRequest<TokensGetInfoRequest> create(long id, String rri) {
			return JsonRpcRequest.create(method(), id, TokensGetInfoRequest.create(rri));
		}
	}

	public static final class TokensGetNativeTokenMethod extends RpcMethodDescriptor<TokensGetNativeTokenRequest, TokenInfo> {
		public static final String METHOD_NAME = "tokens.get_native_token";
		public static final TokensGetNativeTokenMethod INSTANCE = new TokensGetNativeTokenMethod();

		private TokensGetNativeTokenMethod() {
			super(TokensGetNativeTokenRequest.class, new TypeReference<>() {}, METHOD_NAME, ARCHIVE);
		}

		public JsonRpcRequest<TokensGetNativeTokenRequest> create(long id) {
			return JsonRpcRequest.create(method(), id, TokensGetNativeTokenRequest.create());
		}
	}

	public static final class GetTransactionsMethod extends RpcMethodDescriptor<GetTransactionsRequest, TransactionsDTO> {
		public static final String METHOD_NAME = "get_transactions";
		public static final GetTransactionsMethod INSTANCE = new GetTransactionsMethod();

		private GetTransactionsMethod() {
			super(GetTransactionsRequest.class, new TypeReference<>() {}, METHOD_NAME, TRANSACTIONS);
		}

		public JsonRpcRequest<GetTransactionsRequest> create(long id, long limit, OptionalLong offset) {
			return JsonRpcRequest.create(method(), id, GetTransactionsRequest.from(limit, offset));
		}
	}

	public static final class TransactionsLookupTransactionMethod extends RpcMethodDescriptor<TransactionsLookupTransactionRequest, TransactionDTO> {
		public static final String METHOD_NAME = "transactions.lookup_transaction";
		public static final TransactionsLookupTransactionMethod INSTANCE = new TransactionsLookupTransactionMethod();

		private TransactionsLookupTransactionMethod() {
			super(TransactionsLookupTransactionRequest.class, new TypeReference<>() {}, METHOD_NAME, ARCHIVE);
		}

		public JsonRpcRequest<TransactionsLookupTransactionRequest> create(long id, AID txId) {
			return JsonRpcRequest.create(method(), id, TransactionsLookupTransactionRequest.create(txId));
		}
	}

	public static final class TransactionsGetTransactionStatusMethod
		extends RpcMethodDescriptor<TransactionsGetTransactionStatusRequest, TransactionStatusDTO> {
		public static final String METHOD_NAME = "transactions.get_transaction_status";
		public static final TransactionsGetTransactionStatusMethod INSTANCE = new TransactionsGetTransactionStatusMethod();

		private TransactionsGetTransactionStatusMethod() {
			super(TransactionsGetTransactionStatusRequest.class, new TypeReference<>() {}, METHOD_NAME, ARCHIVE);
		}

		public JsonRpcRequest<TransactionsGetTransactionStatusRequest> create(long id, AID txId) {
			return JsonRpcRequest.create(method(), id, TransactionsGetTransactionStatusRequest.create(txId));
		}
	}

	public static final class ValidationGetCurrentEpochDataMethod extends RpcMethodDescriptor<ValidationGetCurrentEpochDataRequest, EpochData> {
		public static final String METHOD_NAME = "validation.get_current_epoch_data";
		public static final ValidationGetCurrentEpochDataMethod INSTANCE = new ValidationGetCurrentEpochDataMethod();

		private ValidationGetCurrentEpochDataMethod() {
			super(ValidationGetCurrentEpochDataRequest.class, new TypeReference<>() {}, METHOD_NAME, VALIDATION);
		}

		public JsonRpcRequest<ValidationGetCurrentEpochDataRequest> create(long id) {
			return JsonRpcRequest.create(method(), id, ValidationGetCurrentEpochDataRequest.create());
		}
	}

	public static final class ValidationGetNodeInfoMethod extends RpcMethodDescriptor<ValidationGetNodeInfoRequest, LocalValidatorInfo> {
		public static final String METHOD_NAME = "validation.get_node_info";
		public static final ValidationGetNodeInfoMethod INSTANCE = new ValidationGetNodeInfoMethod();

		private ValidationGetNodeInfoMethod() {
			super(ValidationGetNodeInfoRequest.class, new TypeReference<>() {}, METHOD_NAME, VALIDATION);
		}

		public JsonRpcRequest<ValidationGetNodeInfoRequest> create(long id) {
			return JsonRpcRequest.create(method(), id, ValidationGetNodeInfoRequest.create());
		}
	}

	public static final class ValidatorsGetNextEpochSetMethod extends RpcMethodDescriptor<ValidatorsGetNextEpochSetRequest, ValidatorsResponse> {
		public static final String METHOD_NAME = "validators.get_next_epoch_set";
		public static final ValidatorsGetNextEpochSetMethod INSTANCE = new ValidatorsGetNextEpochSetMethod();

		private ValidatorsGetNextEpochSetMethod() {
			super(ValidatorsGetNextEpochSetRequest.class, new TypeReference<>() {}, METHOD_NAME, ARCHIVE);
		}

		public JsonRpcRequest<ValidatorsGetNextEpochSetRequest> create(long id, long size, Optional<NavigationCursor> cursor) {
			return JsonRpcRequest.create(method(), id, ValidatorsGetNextEpochSetRequest.create(size, cursor));
		}
	}

	public static final class ValidatorsLookupValidatorMethod extends RpcMethodDescriptor<ValidatorsLookupValidatorRequest, ValidatorDTO> {
		public static final String METHOD_NAME = "validators.lookup_validator";
		public static final ValidatorsLookupValidatorMethod INSTANCE = new ValidatorsLookupValidatorMethod();

		private ValidatorsLookupValidatorMethod() {
			super(ValidatorsLookupValidatorRequest.class, new TypeReference<>() {}, METHOD_NAME, ARCHIVE);
		}

		public JsonRpcRequest<ValidatorsLookupValidatorRequest> create(long id, ValidatorAddress address) {
			return JsonRpcRequest.create(method(), id, ValidatorsLookupValidatorRequest.create(address));
		}
	}
}

