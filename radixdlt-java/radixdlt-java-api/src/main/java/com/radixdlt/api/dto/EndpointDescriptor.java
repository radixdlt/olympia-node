package com.radixdlt.api.dto;

import com.fasterxml.jackson.core.type.TypeReference;
import com.radixdlt.api.dto.request.AccountGetBalancesRequest;
import com.radixdlt.api.dto.request.AccountGetInfoRequest;
import com.radixdlt.api.dto.request.AccountGetStakePositionsRequest;
import com.radixdlt.api.dto.request.AccountGetTransactionHistoryRequest;
import com.radixdlt.api.dto.request.AccountGetUnstakePositionsRequest;
import com.radixdlt.api.dto.request.AccountSubmitTransactionSingleStepRequest;
import com.radixdlt.api.dto.request.ApiGetConfigurationRequest;
import com.radixdlt.api.dto.request.ApiGetDataRequest;
import com.radixdlt.api.dto.request.BftGetConfigurationRequest;
import com.radixdlt.api.dto.request.BftGetDataRequest;
import com.radixdlt.api.dto.request.CheckpointsGetCheckpointsRequest;
import com.radixdlt.api.dto.request.ConstructionBuildTransactionRequest;
import com.radixdlt.api.dto.request.ConstructionFinalizeTransactionRequest;
import com.radixdlt.api.dto.request.ConstructionSubmitTransactionRequest;
import com.radixdlt.api.dto.request.GetTransactionsRequest;
import com.radixdlt.api.dto.request.LedgerGetLatestEpochProofRequest;
import com.radixdlt.api.dto.request.LedgerGetLatestProofRequest;
import com.radixdlt.api.dto.request.MempoolGetConfigurationRequest;
import com.radixdlt.api.dto.request.MempoolGetDataRequest;
import com.radixdlt.api.dto.request.NetworkGetDemandRequest;
import com.radixdlt.api.dto.request.NetworkGetIdRequest;
import com.radixdlt.api.dto.request.NetworkGetThroughputRequest;
import com.radixdlt.api.dto.request.NetworkingGetAddressBookRequest;
import com.radixdlt.api.dto.request.NetworkingGetConfigurationRequest;
import com.radixdlt.api.dto.request.NetworkingGetDataRequest;
import com.radixdlt.api.dto.request.NetworkingGetPeersRequest;
import com.radixdlt.api.dto.request.RadixEngineGetConfigurationRequest;
import com.radixdlt.api.dto.request.RadixEngineGetDataRequest;
import com.radixdlt.api.dto.request.SyncGetConfigurationRequest;
import com.radixdlt.api.dto.request.SyncGetDataRequest;
import com.radixdlt.api.dto.request.TokensGetInfoRequest;
import com.radixdlt.api.dto.request.TokensGetNativeTokenRequest;
import com.radixdlt.api.dto.request.TransactionsGetTransactionStatusRequest;
import com.radixdlt.api.dto.request.TransactionsLookupTransactionRequest;
import com.radixdlt.api.dto.request.ValidationGetCurrentEpochDataRequest;
import com.radixdlt.api.dto.request.ValidationGetNodeInfoRequest;
import com.radixdlt.api.dto.request.ValidatorsGetNextEpochSetRequest;
import com.radixdlt.api.dto.request.ValidatorsLookupValidatorRequest;
import com.radixdlt.api.dto.response.AddressBookEntry;
import com.radixdlt.api.dto.response.ApiConfiguration;
import com.radixdlt.api.dto.response.ApiData;
import com.radixdlt.api.dto.response.BuiltTransaction;
import com.radixdlt.api.dto.response.Checkpoint;
import com.radixdlt.api.dto.response.ConsensusConfiguration;
import com.radixdlt.api.dto.response.ConsensusData;
import com.radixdlt.api.dto.response.EpochData;
import com.radixdlt.api.dto.response.ForkDetails;
import com.radixdlt.api.dto.response.LocalAccount;
import com.radixdlt.api.dto.response.LocalValidatorInfo;
import com.radixdlt.api.dto.response.MempoolConfiguration;
import com.radixdlt.api.dto.response.MempoolData;
import com.radixdlt.api.dto.response.NetworkConfiguration;
import com.radixdlt.api.dto.response.NetworkData;
import com.radixdlt.api.dto.response.NetworkId;
import com.radixdlt.api.dto.response.NetworkPeer;
import com.radixdlt.api.dto.response.NetworkStats;
import com.radixdlt.api.dto.response.Proof;
import com.radixdlt.api.dto.response.RadixEngineData;
import com.radixdlt.api.dto.response.StakePositions;
import com.radixdlt.api.dto.response.SyncConfiguration;
import com.radixdlt.api.dto.response.SyncData;
import com.radixdlt.api.dto.response.TokenBalances;
import com.radixdlt.api.dto.response.TokenInfo;
import com.radixdlt.api.dto.response.TransactionDTO;
import com.radixdlt.api.dto.response.TransactionHistory;
import com.radixdlt.api.dto.response.TransactionStatusDTO;
import com.radixdlt.api.dto.response.TransactionsDTO;
import com.radixdlt.api.dto.response.TxBlobDTO;
import com.radixdlt.api.dto.response.TxDTO;
import com.radixdlt.api.dto.response.UnstakePositions;
import com.radixdlt.api.dto.response.ValidatorDTO;
import com.radixdlt.api.dto.response.ValidatorsResponse;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class EndpointDescriptor<I, O> {
	public static final EndpointDescriptor<AccountGetBalancesRequest, TokenBalances> ACCOUNT_GET_BALANCES
		= descriptor(AccountGetBalancesRequest.class, new TypeReference<>() {});
	public static final EndpointDescriptor<AccountGetInfoRequest, LocalAccount> ACCOUNT_GET_INFO
		= descriptor(AccountGetInfoRequest.class, new TypeReference<>() {});
	public static final EndpointDescriptor<AccountGetTransactionHistoryRequest, TransactionHistory> ACCOUNT_GET_TRANSACTION_HISTORY
		= descriptor(AccountGetTransactionHistoryRequest.class, new TypeReference<>() {});
	public static final EndpointDescriptor<AccountSubmitTransactionSingleStepRequest, TxDTO> ACCOUNT_SUBMIT_TRANSACTION_SINGLE_STEP
		= descriptor(AccountSubmitTransactionSingleStepRequest.class, new TypeReference<>() {});
	public static final EndpointDescriptor<ApiGetConfigurationRequest, ApiConfiguration> API_GET_CONFIGURATION
		= descriptor(ApiGetConfigurationRequest.class, new TypeReference<>() {});
	public static final EndpointDescriptor<ApiGetDataRequest, ApiData> API_GET_DATA
		= descriptor(ApiGetDataRequest.class, new TypeReference<>() {});
	public static final EndpointDescriptor<BftGetConfigurationRequest, ConsensusConfiguration> BFT_GET_CONFIGURATION
		= descriptor(BftGetConfigurationRequest.class, new TypeReference<>() {});
	public static final EndpointDescriptor<BftGetDataRequest, ConsensusData> BFT_GET_DATA
		= descriptor(BftGetDataRequest.class, new TypeReference<>() {});
	public static final EndpointDescriptor<CheckpointsGetCheckpointsRequest, Checkpoint> CHECKPOINTS_GET_CHECKPOINTS
		= descriptor(CheckpointsGetCheckpointsRequest.class, new TypeReference<>() {});
	public static final EndpointDescriptor<ConstructionBuildTransactionRequest, BuiltTransaction> CONSTRUCTION_BUILD_TRANSACTION
		= descriptor(ConstructionBuildTransactionRequest.class, new TypeReference<>() {});
	public static final EndpointDescriptor<ConstructionFinalizeTransactionRequest, TxBlobDTO> CONSTRUCTION_FINALIZE_TRANSACTION
		= descriptor(ConstructionFinalizeTransactionRequest.class, new TypeReference<>() {});
	public static final EndpointDescriptor<ConstructionSubmitTransactionRequest, TxDTO> CONSTRUCTION_SUBMIT_TRANSACTION
		= descriptor(ConstructionSubmitTransactionRequest.class, new TypeReference<>() {});
	public static final EndpointDescriptor<GetTransactionsRequest, TransactionsDTO> GET_TRANSACTIONS
		= descriptor(GetTransactionsRequest.class, new TypeReference<>() {});
	public static final EndpointDescriptor<LedgerGetLatestEpochProofRequest, Proof> LEDGER_GET_LATEST_EPOCH_PROOF
		= descriptor(LedgerGetLatestEpochProofRequest.class, new TypeReference<>() {});
	public static final EndpointDescriptor<LedgerGetLatestProofRequest, Proof> LEDGER_GET_LATEST_PROOF
		= descriptor(LedgerGetLatestProofRequest.class, new TypeReference<>() {});
	public static final EndpointDescriptor<MempoolGetConfigurationRequest, MempoolConfiguration> MEMPOOL_GET_CONFIGURATION
		= descriptor(MempoolGetConfigurationRequest.class, new TypeReference<>() {});
	public static final EndpointDescriptor<MempoolGetDataRequest, MempoolData> MEMPOOL_GET_DATA
		= descriptor(MempoolGetDataRequest.class, new TypeReference<>() {});
	public static final EndpointDescriptor<NetworkGetDemandRequest, NetworkStats> NETWORK_GET_DEMAND
		= descriptor(NetworkGetDemandRequest.class, new TypeReference<>() {});
	public static final EndpointDescriptor<NetworkGetIdRequest, NetworkId> NETWORK_GET_ID
		= descriptor(NetworkGetIdRequest.class, new TypeReference<>() {});
	public static final EndpointDescriptor<NetworkGetThroughputRequest, NetworkStats> NETWORK_GET_THROUGHPUT
		= descriptor(NetworkGetThroughputRequest.class, new TypeReference<>() {});
	public static final EndpointDescriptor<NetworkingGetDataRequest, NetworkData> NETWORKING_GET_DATA
		= descriptor(NetworkingGetDataRequest.class, new TypeReference<>() {});
	public static final EndpointDescriptor<NetworkingGetConfigurationRequest, NetworkConfiguration> NETWORKING_GET_CONFIGURATION
		= descriptor(NetworkingGetConfigurationRequest.class, new TypeReference<>() {});
	public static final EndpointDescriptor<RadixEngineGetDataRequest, RadixEngineData> RADIX_ENGINE_GET_DATA
		= descriptor(RadixEngineGetDataRequest.class, new TypeReference<>() {});
	public static final EndpointDescriptor<SyncGetConfigurationRequest, SyncConfiguration> SYNC_GET_CONFIGURATION
		= descriptor(SyncGetConfigurationRequest.class, new TypeReference<>() {});
	public static final EndpointDescriptor<SyncGetDataRequest, SyncData> SYNC_GET_DATA
		= descriptor(SyncGetDataRequest.class, new TypeReference<>() {});
	public static final EndpointDescriptor<TokensGetInfoRequest, TokenInfo> TOKENS_GET_INFO
		= descriptor(TokensGetInfoRequest.class, new TypeReference<>() {});
	public static final EndpointDescriptor<TokensGetNativeTokenRequest, TokenInfo> TOKENS_GET_NATIVE_TOKEN
		= descriptor(TokensGetNativeTokenRequest.class, new TypeReference<>() {});
	public static final EndpointDescriptor<TransactionsGetTransactionStatusRequest, TransactionStatusDTO> TRANSACTIONS_GET_TRANSACTION_STATUS
		= descriptor(TransactionsGetTransactionStatusRequest.class, new TypeReference<>() {});
	public static final EndpointDescriptor<TransactionsLookupTransactionRequest, TransactionDTO> TRANSACTIONS_LOOKUP_TRANSACTION
		= descriptor(TransactionsLookupTransactionRequest.class, new TypeReference<>() {});
	public static final EndpointDescriptor<ValidationGetCurrentEpochDataRequest, EpochData> VALIDATION_GET_CURRENT_EPOCH_DATA
		= descriptor(ValidationGetCurrentEpochDataRequest.class, new TypeReference<>() {});
	public static final EndpointDescriptor<ValidationGetNodeInfoRequest, LocalValidatorInfo> VALIDATION_GET_NODE_INFO
		= descriptor(ValidationGetNodeInfoRequest.class, new TypeReference<>() {});
	public static final EndpointDescriptor<ValidatorsGetNextEpochSetRequest, ValidatorsResponse> VALIDATORS_GET_NEXT_EPOCH_SET
		= descriptor(ValidatorsGetNextEpochSetRequest.class, new TypeReference<>() {});
	public static final EndpointDescriptor<ValidatorsLookupValidatorRequest, ValidatorDTO> VALIDATORS_LOOKUP_VALIDATOR
		= descriptor(ValidatorsLookupValidatorRequest.class, new TypeReference<>() {});
	public static final EndpointDescriptor<AccountGetStakePositionsRequest, List<StakePositions>> ACCOUNT_GET_STAKE_POSITIONS
		= descriptor(AccountGetStakePositionsRequest.class, new TypeReference<>() {});
	public static final EndpointDescriptor<AccountGetUnstakePositionsRequest, List<UnstakePositions>> ACCOUNT_GET_UNSTAKE_POSITIONS
		= descriptor(AccountGetUnstakePositionsRequest.class, new TypeReference<>() {});
	public static final EndpointDescriptor<NetworkingGetAddressBookRequest, List<AddressBookEntry>> NETWORKING_GET_ADDRESS_BOOK
		= descriptor(NetworkingGetAddressBookRequest.class, new TypeReference<>() {});
	public static final EndpointDescriptor<NetworkingGetPeersRequest, List<NetworkPeer>> NETWORKING_GET_PEERS
		= descriptor(NetworkingGetPeersRequest.class, new TypeReference<>() {});
	public static final EndpointDescriptor<RadixEngineGetConfigurationRequest, List<ForkDetails>> RADIX_ENGINE_GET_CONFIGURATION
		= descriptor(RadixEngineGetConfigurationRequest.class, new TypeReference<>() {});

	public static final Set<EndpointDescriptor<?, ?>> ALL_DESCRIPTORS = new HashSet<>();

	private final Class<I> requestType;
	private final TypeReference<O> responseType;

	private EndpointDescriptor(Class<I> requestType, TypeReference<O> responseType) {
		this.requestType = requestType;
		this.responseType = responseType;
	}

	private static <I, O> EndpointDescriptor<I, O> descriptor(Class<I> requestType, TypeReference<O> responseType) {
		var descriptor = new EndpointDescriptor<>(requestType, responseType);

		ALL_DESCRIPTORS.add(descriptor);

		return descriptor;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}

		if (!(o instanceof EndpointDescriptor)) {
			return false;
		}

		var that = (EndpointDescriptor<?, ?>) o;
		return requestType.equals(that.requestType) && responseType.equals(that.responseType);
	}

	@Override
	public int hashCode() {
		return Objects.hash(requestType, responseType);
	}

	@Override
	public String toString() {
		return "EndpointDescriptor<" + requestType.getSimpleName() + ", " + responseType.getType().getTypeName() +">";
	}

	public Class<I> requestType() {
		return requestType;
	}

	public TypeReference<O> responseType() {
		return responseType;
	}
}
