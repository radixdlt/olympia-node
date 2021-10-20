package com.radixdlt.api.dto;

import com.fasterxml.jackson.core.type.TypeReference;
import com.radixdlt.api.dto.request.AccountGetBalancesRequest;
import com.radixdlt.api.dto.request.AccountGetStakePositionsRequest;
import com.radixdlt.api.dto.request.AccountGetTransactionHistoryRequest;
import com.radixdlt.api.dto.request.AccountGetUnstakePositionsRequest;
import com.radixdlt.api.dto.request.AccountSubmitTransactionSingleStepRequest;
import com.radixdlt.api.dto.request.ConstructionBuildTransactionRequest;
import com.radixdlt.api.dto.request.ConstructionFinalizeTransactionRequest;
import com.radixdlt.api.dto.request.ConstructionSubmitTransactionRequest;
import com.radixdlt.api.dto.request.EmptyRequest;
import com.radixdlt.api.dto.request.GetTransactionsRequest;
import com.radixdlt.api.dto.request.TokensGetInfoRequest;
import com.radixdlt.api.dto.request.TransactionsGetTransactionStatusRequest;
import com.radixdlt.api.dto.request.TransactionsLookupTransactionRequest;
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

public final class Descriptor<I, O> {
	public static final Descriptor<AccountGetBalancesRequest, TokenBalances> ACCOUNT_GET_BALANCES
		= descriptor(AccountGetBalancesRequest.class, new TypeReference<>() {});
	public static final Descriptor<EmptyRequest, LocalAccount> ACCOUNT_GET_INFO
		= descriptor(EmptyRequest.class, new TypeReference<>() {});
	public static final Descriptor<AccountGetTransactionHistoryRequest, TransactionHistory> ACCOUNT_GET_TRANSACTION_HISTORY
		= descriptor(AccountGetTransactionHistoryRequest.class, new TypeReference<>() {});
	public static final Descriptor<AccountSubmitTransactionSingleStepRequest, TxDTO> ACCOUNT_SUBMIT_TRANSACTION_SINGLE_STEP
		= descriptor(AccountSubmitTransactionSingleStepRequest.class, new TypeReference<>() {});
	public static final Descriptor<EmptyRequest, ApiConfiguration> API_GET_CONFIGURATION
		= descriptor(EmptyRequest.class, new TypeReference<>() {});
	public static final Descriptor<EmptyRequest, ApiData> API_GET_DATA
		= descriptor(EmptyRequest.class, new TypeReference<>() {});
	public static final Descriptor<EmptyRequest, ConsensusConfiguration> BFT_GET_CONFIGURATION
		= descriptor(EmptyRequest.class, new TypeReference<>() {});
	public static final Descriptor<EmptyRequest, ConsensusData> BFT_GET_DATA
		= descriptor(EmptyRequest.class, new TypeReference<>() {});
	public static final Descriptor<EmptyRequest, Checkpoint> CHECKPOINTS_GET_CHECKPOINTS
		= descriptor(EmptyRequest.class, new TypeReference<>() {});
	public static final Descriptor<ConstructionBuildTransactionRequest, BuiltTransaction> CONSTRUCTION_BUILD_TRANSACTION
		= descriptor(ConstructionBuildTransactionRequest.class, new TypeReference<>() {});
	public static final Descriptor<ConstructionFinalizeTransactionRequest, TxBlobDTO> CONSTRUCTION_FINALIZE_TRANSACTION
		= descriptor(ConstructionFinalizeTransactionRequest.class, new TypeReference<>() {});
	public static final Descriptor<ConstructionSubmitTransactionRequest, TxDTO> CONSTRUCTION_SUBMIT_TRANSACTION
		= descriptor(ConstructionSubmitTransactionRequest.class, new TypeReference<>() {});
	public static final Descriptor<GetTransactionsRequest, TransactionsDTO> GET_TRANSACTIONS
		= descriptor(GetTransactionsRequest.class, new TypeReference<>() {});
	public static final Descriptor<EmptyRequest, Proof> LEDGER_GET_LATEST_EPOCH_PROOF
		= descriptor(EmptyRequest.class, new TypeReference<>() {});
	public static final Descriptor<EmptyRequest, Proof> LEDGER_GET_LATEST_PROOF
		= descriptor(EmptyRequest.class, new TypeReference<>() {});
	public static final Descriptor<EmptyRequest, MempoolConfiguration> MEMPOOL_GET_CONFIGURATION
		= descriptor(EmptyRequest.class, new TypeReference<>() {});
	public static final Descriptor<EmptyRequest, MempoolData> MEMPOOL_GET_DATA
		= descriptor(EmptyRequest.class, new TypeReference<>() {});
	public static final Descriptor<EmptyRequest, NetworkStats> NETWORK_GET_DEMAND
		= descriptor(EmptyRequest.class, new TypeReference<>() {});
	public static final Descriptor<EmptyRequest, NetworkId> NETWORK_GET_ID
		= descriptor(EmptyRequest.class, new TypeReference<>() {});
	public static final Descriptor<EmptyRequest, NetworkStats> NETWORK_GET_THROUGHPUT
		= descriptor(EmptyRequest.class, new TypeReference<>() {});
	public static final Descriptor<EmptyRequest, NetworkData> NETWORKING_GET_DATA
		= descriptor(EmptyRequest.class, new TypeReference<>() {});
	public static final Descriptor<EmptyRequest, NetworkConfiguration> NETWORKING_GET_CONFIGURATION
		= descriptor(EmptyRequest.class, new TypeReference<>() {});
	public static final Descriptor<EmptyRequest, RadixEngineData> RADIX_ENGINE_GET_DATA
		= descriptor(EmptyRequest.class, new TypeReference<>() {});
	public static final Descriptor<EmptyRequest, SyncConfiguration> SYNC_GET_CONFIGURATION
		= descriptor(EmptyRequest.class, new TypeReference<>() {});
	public static final Descriptor<EmptyRequest, SyncData> SYNC_GET_DATA
		= descriptor(EmptyRequest.class, new TypeReference<>() {});
	public static final Descriptor<TokensGetInfoRequest, TokenInfo> TOKENS_GET_INFO
		= descriptor(TokensGetInfoRequest.class, new TypeReference<>() {});
	public static final Descriptor<EmptyRequest, TokenInfo> TOKENS_GET_NATIVE_TOKEN
		= descriptor(EmptyRequest.class, new TypeReference<>() {});
	public static final Descriptor<TransactionsGetTransactionStatusRequest, TransactionStatusDTO> TRANSACTIONS_GET_TRANSACTION_STATUS
		= descriptor(TransactionsGetTransactionStatusRequest.class, new TypeReference<>() {});
	public static final Descriptor<TransactionsLookupTransactionRequest, TransactionDTO> TRANSACTIONS_LOOKUP_TRANSACTION
		= descriptor(TransactionsLookupTransactionRequest.class, new TypeReference<>() {});
	public static final Descriptor<EmptyRequest, EpochData> VALIDATION_GET_CURRENT_EPOCH_DATA
		= descriptor(EmptyRequest.class, new TypeReference<>() {});
	public static final Descriptor<EmptyRequest, LocalValidatorInfo> VALIDATION_GET_NODE_INFO
		= descriptor(EmptyRequest.class, new TypeReference<>() {});
	public static final Descriptor<ValidatorsGetNextEpochSetRequest, ValidatorsResponse> VALIDATORS_GET_NEXT_EPOCH_SET
		= descriptor(ValidatorsGetNextEpochSetRequest.class, new TypeReference<>() {});
	public static final Descriptor<ValidatorsLookupValidatorRequest, ValidatorDTO> VALIDATORS_LOOKUP_VALIDATOR
		= descriptor(ValidatorsLookupValidatorRequest.class, new TypeReference<>() {});
	public static final Descriptor<AccountGetStakePositionsRequest, List<StakePositions>> ACCOUNT_GET_STAKE_POSITIONS
		= descriptor(AccountGetStakePositionsRequest.class, new TypeReference<>() {});
	public static final Descriptor<AccountGetUnstakePositionsRequest, List<UnstakePositions>> ACCOUNT_GET_UNSTAKE_POSITIONS
		= descriptor(AccountGetUnstakePositionsRequest.class, new TypeReference<>() {});
	public static final Descriptor<EmptyRequest, List<AddressBookEntry>> NETWORKING_GET_ADDRESS_BOOK
		= descriptor(EmptyRequest.class, new TypeReference<>() {});
	public static final Descriptor<EmptyRequest, List<NetworkPeer>> NETWORKING_GET_PEERS
		= descriptor(EmptyRequest.class, new TypeReference<>() {});
	public static final Descriptor<EmptyRequest, List<ForkDetails>> RADIX_ENGINE_GET_CONFIGURATION
		= descriptor(EmptyRequest.class, new TypeReference<>() {});

	public static final Set<Descriptor<?, ?>> ALL_DESCRIPTORS = new HashSet<>();

	private final Class<I> requestType;
	private final TypeReference<O> responseType;

	private Descriptor(Class<I> requestType, TypeReference<O> responseType) {
		this.requestType = requestType;
		this.responseType = responseType;
	}

	private static <I, O> Descriptor<I, O> descriptor(Class<I> requestType, TypeReference<O> responseType) {
		var descriptor = new Descriptor<>(requestType, responseType);

		ALL_DESCRIPTORS.add(descriptor);

		return descriptor;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}

		if (!(o instanceof Descriptor)) {
			return false;
		}

		var that = (Descriptor<?, ?>) o;
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
