package com.radixdlt.client.lib.api.rpc;

import static com.radixdlt.client.lib.api.rpc.EndPoint.*;

public enum RpcMethod {
	TOKEN_NATIVE("tokens.get_native_token", ARCHIVE),
	TOKEN_INFO("tokens.get_info", ARCHIVE),

	ACCOUNT_BALANCES("account.get_balances", ARCHIVE),
	ACCOUNT_HISTORY("account.get_transaction_history", ARCHIVE),
	ACCOUNT_STAKES("account.get_stake_positions", ARCHIVE),
	ACCOUNT_UNSTAKES("account.get_unstake_positions", ARCHIVE),

	TRANSACTION_LOOKUP("transactions.lookup_transaction", ARCHIVE),
	TRANSACTION_STATUS("transactions.get_transaction_status", ARCHIVE),

	NETWORK_ID("network.get_id", ARCHIVE),
	NETWORK_THROUGHPUT("network.get_throughput", ARCHIVE),
	NETWORK_DEMAND("network.get_demand", ARCHIVE),

	NETWORK_CONFIG("networking.get_configuration", SYSTEM),
	NETWORK_PEERS("networking.get_peers", SYSTEM),
	NETWORK_DATA("networking.get_data", SYSTEM),
	NETWORK_ADDRESS_BOOK("networking.get_address_book", SYSTEM),

	VALIDATORS_LIST("validators.get_next_epoch_set", ARCHIVE),
	VALIDATORS_LOOKUP("validators.lookup_validator", ARCHIVE),

	CONSTRUCTION_BUILD("construction.build_transaction", CONSTRUCTION),
	CONSTRUCTION_FINALIZE("construction.finalize_transaction", CONSTRUCTION),
	CONSTRUCTION_SUBMIT("construction.submit_transaction", CONSTRUCTION),


	TRANSACTION_LIST("get_transactions", TRANSACTIONS),

	API_CONFIGURATION("api.get_configuration", SYSTEM),
	API_DATA("api.get_data", SYSTEM),

	BFT_CONFIGURATION("bft.get_configuration", SYSTEM),
	BFT_DATA("bft.get_data", SYSTEM),

	MEMPOOL_CONFIGURATION("mempool.get_configuration", SYSTEM),
	MEMPOOL_DATA("mempool.get_data", SYSTEM),

	LEDGER_PROOF("ledger.get_latest_proof", SYSTEM),
	LEDGER_EPOCH_PROOF("ledger.get_latest_epoch_proof", SYSTEM),
	LEDGER_CHECKPOINTS("checkpoints.get_checkpoints", SYSTEM),

	RADIX_ENGINE_CONFIGURATION("radix_engine.get_configuration", SYSTEM),
	RADIX_ENGINE_DATA("radix_engine.get_data", SYSTEM),

	SYNC_CONFIGURATION("sync.get_configuration", SYSTEM),
	SYNC_DATA("sync.get_data", SYSTEM),

	VALIDATION_NODE_INFO("validation.get_node_info", VALIDATION),
	VALIDATION_CURRENT_EPOCH("validation.get_current_epoch_data", VALIDATION),

	ACCOUNT_INFO("account.get_info", ACCOUNT),
	ACCOUNT_SUBMIT_SINGLE_STEP("account.submit_transaction_single_step", ACCOUNT);

	private final String method;
	private final EndPoint endPoint;

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
}
