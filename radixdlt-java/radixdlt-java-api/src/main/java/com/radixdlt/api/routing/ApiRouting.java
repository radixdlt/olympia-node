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

package com.radixdlt.api.routing;

import com.radixdlt.api.dto.EndpointDescriptor;

import java.util.HashMap;
import java.util.Map;

import static com.radixdlt.api.routing.Route.endPoint;
import static com.radixdlt.api.routing.RoutingTable.group;
import static com.radixdlt.api.routing.RoutingTable.table;

public final class ApiRouting {
	private ApiRouting() { }

	public static final RoutingTable ARCHIVE = table(
		group(
			"/archive",
			group(
				"/tokens",
				endPoint("/get_native_token", EndpointDescriptor.TOKENS_GET_NATIVE_TOKEN),
				endPoint("/get_info", EndpointDescriptor.TOKENS_GET_INFO)
			),
			group(
				"/account",
				endPoint("/get_balances", EndpointDescriptor.ACCOUNT_GET_BALANCES),
				endPoint("/get_transaction_history", EndpointDescriptor.ACCOUNT_GET_TRANSACTION_HISTORY),
				endPoint("/get_stake_positions", EndpointDescriptor.ACCOUNT_GET_STAKE_POSITIONS),
				endPoint("/get_unstake_positions", EndpointDescriptor.ACCOUNT_GET_UNSTAKE_POSITIONS)
			),
			group(
				"/transactions",
				endPoint("/lookup_transaction", EndpointDescriptor.TRANSACTIONS_LOOKUP_TRANSACTION),
				endPoint("/get_transaction_status", EndpointDescriptor.TRANSACTIONS_GET_TRANSACTION_STATUS)
			),
			group(
				"/network",
				endPoint("/get_id", EndpointDescriptor.NETWORK_GET_ID),
				endPoint("/get_throughput", EndpointDescriptor.NETWORK_GET_THROUGHPUT),
				endPoint("/get_demand", EndpointDescriptor.NETWORK_GET_DEMAND)
			),
			group(
				"/validators",
				endPoint("/get_next_epoch_set", EndpointDescriptor.VALIDATORS_GET_NEXT_EPOCH_SET),
				endPoint("/lookup_validator", EndpointDescriptor.VALIDATORS_LOOKUP_VALIDATOR)
			)
		)
	);

	public static final RoutingTable CONSTRUCTION = table(group(
		"/construction",
		endPoint("/build_transaction", EndpointDescriptor.CONSTRUCTION_BUILD_TRANSACTION),
		endPoint("/finalize_transaction", EndpointDescriptor.CONSTRUCTION_FINALIZE_TRANSACTION),
		endPoint("/submit_transaction", EndpointDescriptor.CONSTRUCTION_SUBMIT_TRANSACTION)
	));

	public static final RoutingTable SYSTEM = table(
		group(
			"/system",
			group(
				"/networking",
				endPoint("/get_configuration", EndpointDescriptor.NETWORKING_GET_CONFIGURATION),
				endPoint("/get_peers", EndpointDescriptor.NETWORKING_GET_PEERS),
				endPoint("/get_data", EndpointDescriptor.NETWORKING_GET_DATA),
				endPoint("/get_address_book", EndpointDescriptor.NETWORKING_GET_ADDRESS_BOOK)
			),
			group(
				"/api",
				endPoint("/get_configuration", EndpointDescriptor.API_GET_CONFIGURATION),
				endPoint("/get_data", EndpointDescriptor.API_GET_DATA)
			),
			group(
				"/bft",
				endPoint("/get_configuration", EndpointDescriptor.BFT_GET_CONFIGURATION),
				endPoint("/get_data", EndpointDescriptor.BFT_GET_DATA)
			),
			group(
				"/mempool",
				endPoint("/get_configuration", EndpointDescriptor.MEMPOOL_GET_CONFIGURATION),
				endPoint("/get_data", EndpointDescriptor.MEMPOOL_GET_DATA)
			),
			group(
				"/ledger",
				endPoint("/get_latest_proof", EndpointDescriptor.LEDGER_GET_LATEST_PROOF),
				endPoint("/get_latest_epoch_proof", EndpointDescriptor.LEDGER_GET_LATEST_EPOCH_PROOF)
			),
			group(
				"/checkpoints",
				endPoint("checkpoints.get_checkpoints", EndpointDescriptor.CHECKPOINTS_GET_CHECKPOINTS)
			),
			group(
				"/engine",
				endPoint("/get_configuration", EndpointDescriptor.RADIX_ENGINE_GET_CONFIGURATION),
				endPoint("/get_data", EndpointDescriptor.RADIX_ENGINE_GET_DATA)
			),
			group(
				"/sync",
				endPoint("/get_configuration", EndpointDescriptor.SYNC_GET_CONFIGURATION),
				endPoint("/get_data", EndpointDescriptor.SYNC_GET_DATA)
			)
		)
	);


	public static final RoutingTable TRANSACTIONS = table(
		group(
			"/transactions",
			endPoint("/get_transactions", EndpointDescriptor.GET_TRANSACTIONS)
		)
	);

	public static final RoutingTable VALIDATION = table(
		group(
			"/validation",
			endPoint("/get_node_info", EndpointDescriptor.VALIDATION_GET_NODE_INFO),
			endPoint("/get_current_epoch_data", EndpointDescriptor.VALIDATION_GET_CURRENT_EPOCH_DATA)
		)
	);

	public static final RoutingTable ACCOUNT = table(group(
		"/account",
		endPoint("/get_info", EndpointDescriptor.ACCOUNT_GET_INFO),
		endPoint("/submit_transaction_single_step", EndpointDescriptor.ACCOUNT_SUBMIT_TRANSACTION_SINGLE_STEP)
	));

	public static final Map<String, RoutingTable> ALL_ROUTING_TABLES;

	public static final Map<String, RoutingTable> ARCHIVE_TABLES = Map.of(
		"archive", ARCHIVE,
		"construction", CONSTRUCTION
	);

	public static final Map<String, RoutingTable> NODE_TABLES = Map.of(
		"account", ACCOUNT,
		"system", SYSTEM,
		"transactions", TRANSACTIONS,
		"validation", VALIDATION
	);

	static {
		var map = new HashMap<String, RoutingTable>();
		map.putAll(ARCHIVE_TABLES);
		map.putAll(NODE_TABLES);
		ALL_ROUTING_TABLES = Map.copyOf(map);
	}
}
