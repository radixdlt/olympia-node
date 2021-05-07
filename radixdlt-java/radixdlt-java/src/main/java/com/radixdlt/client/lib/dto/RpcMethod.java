package com.radixdlt.client.lib.dto;

public enum RpcMethod {
	NETWORK_ID("networkId"),
	NATIVE_TOKEN("nativeToken"),
	TOKEN_INFO("tokenInfo"),
	TOKEN_BALANCES("tokenBalances"),
	TRANSACTION_HISTORY("transactionHistory"),
	LOOKUP_TRANSACTION("lookupTransaction"),
	STAKE_POSITIONS("stakePositions"),
	UNSTAKE_POSITIONS("unstakePositions"),
	STATUS_OF_TRANSACTION("statusOfTransaction"),
	NETWORK_TRANSACTION_THROUGHPUT("networkTransactionThroughput"),
	NETWORK_TRANSACTION_DEMAND("networkTransactionDemand"),
	VALIDATORS("validators"),
	LOOKUP_VALIDATOR("lookupValidator"),
	BUILD_TRANSACTION("buildTransaction"),
	FINALIZE_TRANSACTION("finalizeTransaction"),
	SUBMIT_TRANSACTION("submitTransaction");

	private final String method;

	RpcMethod(String method) {
		this.method = method;
	}

	public String method() {
		return method;
	}
}
