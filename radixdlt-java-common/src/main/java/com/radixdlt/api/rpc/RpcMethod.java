package com.radixdlt.api.rpc;

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
import com.radixdlt.utils.functional.Functions;

import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.radixdlt.api.rpc.EndPoint.ACCOUNT;
import static com.radixdlt.api.rpc.EndPoint.ARCHIVE;
import static com.radixdlt.api.rpc.EndPoint.CONSTRUCTION;
import static com.radixdlt.api.rpc.EndPoint.SYSTEM;
import static com.radixdlt.api.rpc.EndPoint.TRANSACTIONS;
import static com.radixdlt.api.rpc.EndPoint.VALIDATION;

//TODO: move request creation here?
//TODO: consider reworking it into RpcMethod<Request, Response>???
public enum RpcMethod {
	TOKEN_NATIVE(TokensGetNativeToken.METHOD_NAME, ARCHIVE),
	TOKEN_INFO(TokensGetInfo.METHOD_NAME, ARCHIVE),
	ACCOUNT_BALANCES(AccountGetBalances.METHOD_NAME, ARCHIVE),
	ACCOUNT_HISTORY(AccountGetTransactionHistory.METHOD_NAME, ARCHIVE),
	ACCOUNT_STAKES(AccountGetStakePositions.METHOD_NAME, ARCHIVE),
	ACCOUNT_UNSTAKES(AccountGetUnstakePositions.METHOD_NAME, ARCHIVE),
	TRANSACTION_LOOKUP(TransactionsLookupTransaction.METHOD_NAME, ARCHIVE),
	TRANSACTION_STATUS(TransactionsGetTransactionStatus.METHOD_NAME, ARCHIVE),
	NETWORK_ID(NetworkGetId.METHOD_NAME, ARCHIVE),
	NETWORK_THROUGHPUT(NetworkGetThroughput.METHOD_NAME, ARCHIVE),
	NETWORK_DEMAND(NetworkGetDemand.METHOD_NAME, ARCHIVE),
	NETWORK_CONFIG(NetworkingGetConfiguration.METHOD_NAME, SYSTEM),
	NETWORK_PEERS(NetworkingGetPeers.METHOD_NAME, SYSTEM),
	NETWORK_DATA(NetworkingGetData.METHOD_NAME, SYSTEM),
	NETWORK_ADDRESS_BOOK(NetworkingGetAddressBook.METHOD_NAME, SYSTEM),
	VALIDATORS_LIST(ValidatorsGetNextEpochSet.METHOD_NAME, ARCHIVE),
	VALIDATORS_LOOKUP(ValidatorsLookupValidator.METHOD_NAME, ARCHIVE),
	CONSTRUCTION_BUILD(ConstructionBuildTransaction.METHOD_NAME, CONSTRUCTION),
	CONSTRUCTION_FINALIZE(ConstructionFinalizeTransaction.METHOD_NAME, CONSTRUCTION),
	CONSTRUCTION_SUBMIT(ConstructionSubmitTransaction.METHOD_NAME, CONSTRUCTION),
	TRANSACTION_LIST(GetTransactions.METHOD_NAME, TRANSACTIONS),
	API_CONFIGURATION(ApiGetConfiguration.METHOD_NAME, SYSTEM),
	API_DATA(ApiGetData.METHOD_NAME, SYSTEM),
	BFT_CONFIGURATION(BftGetConfiguration.METHOD_NAME, SYSTEM),
	BFT_DATA(BftGetData.METHOD_NAME, SYSTEM),
	MEMPOOL_CONFIGURATION(MempoolGetConfiguration.METHOD_NAME, SYSTEM),
	MEMPOOL_DATA(MempoolGetData.METHOD_NAME, SYSTEM),
	LEDGER_PROOF(LedgerGetLatestProof.METHOD_NAME, SYSTEM),
	LEDGER_EPOCH_PROOF(LedgerGetLatestEpochProof.METHOD_NAME, SYSTEM),
	LEDGER_CHECKPOINTS(CheckpointsGetCheckpoints.METHOD_NAME, SYSTEM),
	RADIX_ENGINE_CONFIGURATION(RadixEngineGetConfiguration.METHOD_NAME, SYSTEM),
	RADIX_ENGINE_DATA(RadixEngineGetData.METHOD_NAME, SYSTEM),
	SYNC_CONFIGURATION(SyncGetConfiguration.METHOD_NAME, SYSTEM),
	SYNC_DATA(SyncGetData.METHOD_NAME, SYSTEM),
	VALIDATION_NODE_INFO(ValidationGetNodeInfo.METHOD_NAME, VALIDATION),
	VALIDATION_CURRENT_EPOCH(ValidationGetCurrentEpochData.METHOD_NAME, VALIDATION),
	ACCOUNT_INFO(AccountGetInfo.METHOD_NAME, ACCOUNT),
	ACCOUNT_SUBMIT_SINGLE_STEP(AccountSubmitTransactionSingleStep.METHOD_NAME, ACCOUNT);

	private final String method;
	private final EndPoint endPoint;

	private static final Map<String, RpcMethod> BY_NAME = Stream.of(RpcMethod.values())
		.collect(Collectors.toMap(RpcMethod::method, Functions::identity));

	RpcMethod(String method, EndPoint endPoint) {
		this.method = method;
		this.endPoint = endPoint;
	}

	public String method() {
		return method;
	}

	public EndPoint endPoint() {
		return endPoint;
	}

	public static Optional<RpcMethod> fromString(String method) {
		return Optional.ofNullable(BY_NAME.get(method));
	}
}

