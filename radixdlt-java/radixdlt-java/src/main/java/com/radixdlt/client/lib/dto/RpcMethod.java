package com.radixdlt.client.lib.dto;

import static com.radixdlt.client.lib.dto.EndPoint.ARCHIVE;
import static com.radixdlt.client.lib.dto.EndPoint.CONSTRUCTION;

public enum RpcMethod {
	NATIVE_TOKEN("tokens.get_native_token", ARCHIVE),
	TOKEN_INFO("tokens.get_info", ARCHIVE),
	TOKEN_BALANCES("account.get_balances", ARCHIVE),
	TRANSACTION_HISTORY("account.get_transaction_history", ARCHIVE),
	LOOKUP_TRANSACTION("transactions.lookup_transaction", ARCHIVE),
	STAKE_POSITIONS("account.get_stake_positions", ARCHIVE),
	UNSTAKE_POSITIONS("account.get_unstake_positions", ARCHIVE),
	STATUS_OF_TRANSACTION("transactions.get_transaction_status", ARCHIVE),
	NETWORK_ID("network.get_id", ARCHIVE),
	NETWORK_TRANSACTION_THROUGHPUT("network.get_throughput", ARCHIVE),
	NETWORK_TRANSACTION_DEMAND("network.get_demand", ARCHIVE),
	VALIDATORS("validators.get_next_epoch_set", ARCHIVE),
	LOOKUP_VALIDATOR("validators.lookup_validator", ARCHIVE),
	BUILD_TRANSACTION("construction.build_transaction", CONSTRUCTION),
	FINALIZE_TRANSACTION("construction.finalize_transaction", CONSTRUCTION),
	SUBMIT_TRANSACTION("construction.submit_transaction", CONSTRUCTION);

	private final String method;
	private final EndPoint endPoint;

	RpcMethod(String method, EndPoint endPoint) {
		this.method = method;
		this.endPoint = endPoint;
	}

	public String method() {
		return method;
	}

	public String path() {
		return endPoint.path();
	}
}
