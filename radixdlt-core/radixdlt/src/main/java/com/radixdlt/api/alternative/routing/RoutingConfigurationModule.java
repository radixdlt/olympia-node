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

package com.radixdlt.api.alternative.routing;

import com.google.inject.AbstractModule;
import com.radixdlt.api.alternative.handlers.ArchiveHandlers;
import com.radixdlt.api.alternative.handlers.NodeHandlers;
import com.radixdlt.api.alternative.server.RouteMapping;
import com.radixdlt.api.archive.ArchiveServer;
import com.radixdlt.api.node.NodeServer;
import com.radixdlt.api.routing.Route;
import com.radixdlt.api.routing.RouteSource;
import com.radixdlt.api.routing.RoutingTable;
import com.radixdlt.properties.RuntimeProperties;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.radixdlt.api.alternative.routing.EndpointMappingEntry.entry;
import static com.radixdlt.api.dto.EndpointDescriptor.ACCOUNT_GET_BALANCES;
import static com.radixdlt.api.dto.EndpointDescriptor.ACCOUNT_GET_INFO;
import static com.radixdlt.api.dto.EndpointDescriptor.ACCOUNT_GET_STAKE_POSITIONS;
import static com.radixdlt.api.dto.EndpointDescriptor.ACCOUNT_GET_TRANSACTION_HISTORY;
import static com.radixdlt.api.dto.EndpointDescriptor.ACCOUNT_GET_UNSTAKE_POSITIONS;
import static com.radixdlt.api.dto.EndpointDescriptor.ACCOUNT_SUBMIT_TRANSACTION_SINGLE_STEP;
import static com.radixdlt.api.dto.EndpointDescriptor.ALL_DESCRIPTORS;
import static com.radixdlt.api.dto.EndpointDescriptor.API_GET_CONFIGURATION;
import static com.radixdlt.api.dto.EndpointDescriptor.API_GET_DATA;
import static com.radixdlt.api.dto.EndpointDescriptor.BFT_GET_CONFIGURATION;
import static com.radixdlt.api.dto.EndpointDescriptor.BFT_GET_DATA;
import static com.radixdlt.api.dto.EndpointDescriptor.CHECKPOINTS_GET_CHECKPOINTS;
import static com.radixdlt.api.dto.EndpointDescriptor.CONSTRUCTION_BUILD_TRANSACTION;
import static com.radixdlt.api.dto.EndpointDescriptor.CONSTRUCTION_FINALIZE_TRANSACTION;
import static com.radixdlt.api.dto.EndpointDescriptor.CONSTRUCTION_SUBMIT_TRANSACTION;
import static com.radixdlt.api.dto.EndpointDescriptor.GET_TRANSACTIONS;
import static com.radixdlt.api.dto.EndpointDescriptor.LEDGER_GET_LATEST_EPOCH_PROOF;
import static com.radixdlt.api.dto.EndpointDescriptor.LEDGER_GET_LATEST_PROOF;
import static com.radixdlt.api.dto.EndpointDescriptor.MEMPOOL_GET_CONFIGURATION;
import static com.radixdlt.api.dto.EndpointDescriptor.MEMPOOL_GET_DATA;
import static com.radixdlt.api.dto.EndpointDescriptor.NETWORKING_GET_ADDRESS_BOOK;
import static com.radixdlt.api.dto.EndpointDescriptor.NETWORKING_GET_CONFIGURATION;
import static com.radixdlt.api.dto.EndpointDescriptor.NETWORKING_GET_DATA;
import static com.radixdlt.api.dto.EndpointDescriptor.NETWORKING_GET_PEERS;
import static com.radixdlt.api.dto.EndpointDescriptor.NETWORK_GET_DEMAND;
import static com.radixdlt.api.dto.EndpointDescriptor.NETWORK_GET_ID;
import static com.radixdlt.api.dto.EndpointDescriptor.NETWORK_GET_THROUGHPUT;
import static com.radixdlt.api.dto.EndpointDescriptor.RADIX_ENGINE_GET_CONFIGURATION;
import static com.radixdlt.api.dto.EndpointDescriptor.RADIX_ENGINE_GET_DATA;
import static com.radixdlt.api.dto.EndpointDescriptor.SYNC_GET_CONFIGURATION;
import static com.radixdlt.api.dto.EndpointDescriptor.SYNC_GET_DATA;
import static com.radixdlt.api.dto.EndpointDescriptor.TOKENS_GET_INFO;
import static com.radixdlt.api.dto.EndpointDescriptor.TOKENS_GET_NATIVE_TOKEN;
import static com.radixdlt.api.dto.EndpointDescriptor.TRANSACTIONS_GET_TRANSACTION_STATUS;
import static com.radixdlt.api.dto.EndpointDescriptor.TRANSACTIONS_LOOKUP_TRANSACTION;
import static com.radixdlt.api.dto.EndpointDescriptor.VALIDATION_GET_CURRENT_EPOCH_DATA;
import static com.radixdlt.api.dto.EndpointDescriptor.VALIDATION_GET_NODE_INFO;
import static com.radixdlt.api.dto.EndpointDescriptor.VALIDATORS_GET_NEXT_EPOCH_SET;
import static com.radixdlt.api.dto.EndpointDescriptor.VALIDATORS_LOOKUP_VALIDATOR;
import static com.radixdlt.api.routing.ApiRouting.ALL_ROUTING_TABLES;
import static com.radixdlt.api.routing.ApiRouting.ARCHIVE_TABLES;
import static com.radixdlt.api.routing.ApiRouting.NODE_TABLES;
import static com.radixdlt.api.routing.RoutingTable.table;

public class RoutingConfigurationModule extends AbstractModule {
	private final int networkId;
	private final RuntimeProperties properties;
	private final ArchiveHandlers archiveHandlers;
	private final NodeHandlers nodeHandlers;

	public RoutingConfigurationModule(int networkId, RuntimeProperties properties, ArchiveHandlers archiveHandlers, NodeHandlers nodeHandlers) {
		this.networkId = networkId;
		this.properties = properties;
		this.archiveHandlers = archiveHandlers;
		this.nodeHandlers = nodeHandlers;
	}

	@Override
	protected void configure() {
		var handlers = getHandlers();

		validateRouting(handlers);

		var endpointStatus = collectEnabledTables();

		var archiveRoutingTable = table("/v2", activeTables(endpointStatus, ARCHIVE_TABLES));
		var nodeRoutingTable = table("/v2", activeTables(endpointStatus, NODE_TABLES));
		var archiveHandlers = collectEnabledHandlers(archiveRoutingTable, handlers);
		var nodeHandlers = collectEnabledHandlers(nodeRoutingTable, handlers);

		bind(RouteMapping.class).annotatedWith(ArchiveServer.class).toInstance(RouteMapping.create(archiveRoutingTable, archiveHandlers));
		bind(RouteMapping.class).annotatedWith(NodeServer.class).toInstance(RouteMapping.create(nodeRoutingTable, nodeHandlers));
	}

	private void validateRouting(List<EndpointMappingEntry<?, ?>> handlers) {
		if (handlers.size() != ALL_DESCRIPTORS.size()) {
			throw new IllegalStateException("Not all handlers configured for all endpoints");
		}

		var descriptors = ALL_ROUTING_TABLES.values().stream()
			.flatMap(RoutingTable::routes)
			.map(Route::descriptor)
			.collect(Collectors.toSet());

		if (descriptors.size() != ALL_DESCRIPTORS.size()) {
			var tmp = new HashSet<>(ALL_DESCRIPTORS);
			tmp.removeAll(descriptors);
			throw new IllegalStateException("Some endpoint descriptors are not routed: " + tmp);
		}
	}

	private Map<String, Boolean> collectEnabledTables() {
		var endpointStatus = new HashMap<String, Boolean>();

		ALL_ROUTING_TABLES.forEach((name, table) -> checkTable(name, endpointStatus));

		return endpointStatus;
	}

	private HandlerTable collectEnabledHandlers(RoutingTable archiveRoutingTable, List<EndpointMappingEntry<?, ?>> handlers) {
		return HandlerTable.create(handlers.stream()
			.filter(entry -> archiveRoutingTable.forApi(entry.descriptor()).isPresent())
			.collect(Collectors.toList()));
	}

	private Stream<RouteSource> activeTables(Map<String, Boolean> endpointStatus, Map<String, RoutingTable> tables) {
		return tables.entrySet().stream()
			.filter(entry -> endpointStatus.get(entry.getKey()))
			.map(Map.Entry::getValue)
			.map(RouteSource.class::cast);
	}

	private void checkTable(String name, Map<String, Boolean> endpointStatus) {
		var enabled = properties.get("api." + name + ".enable", false);
		endpointStatus.put(name, enabled);
	}

	private List<EndpointMappingEntry<?, ?>> getHandlers() {
		return List.of(
			//Archive
			entry(ACCOUNT_GET_BALANCES, archiveHandlers::accountGetBalances),
			entry(ACCOUNT_GET_STAKE_POSITIONS, archiveHandlers::accountGetStakePositions),
			entry(ACCOUNT_GET_TRANSACTION_HISTORY, archiveHandlers::accountGetTransactionHistory),
			entry(ACCOUNT_GET_UNSTAKE_POSITIONS, archiveHandlers::accountGetUnstakePositions),
			entry(CONSTRUCTION_BUILD_TRANSACTION, archiveHandlers::constructionBuildTransaction),
			entry(CONSTRUCTION_FINALIZE_TRANSACTION, archiveHandlers::constructionFinalizeTransaction),
			entry(CONSTRUCTION_SUBMIT_TRANSACTION, archiveHandlers::constructionSubmitTransaction),
			entry(NETWORK_GET_DEMAND, archiveHandlers::networkGetDemand),
			entry(NETWORK_GET_ID, archiveHandlers::networkGetId),
			entry(NETWORK_GET_THROUGHPUT, archiveHandlers::networkGetThroughput),
			entry(TOKENS_GET_INFO, archiveHandlers::tokensGetInfo),
			entry(TOKENS_GET_NATIVE_TOKEN, archiveHandlers::tokensGetNativeToken),
			entry(TRANSACTIONS_GET_TRANSACTION_STATUS, archiveHandlers::transactionsGetTransactionStatus),
			entry(TRANSACTIONS_LOOKUP_TRANSACTION, archiveHandlers::transactionsLookupTransaction),
			entry(VALIDATORS_GET_NEXT_EPOCH_SET, archiveHandlers::validatorsGetNextEpochSet),
			entry(VALIDATORS_LOOKUP_VALIDATOR, archiveHandlers::validatorsLookupValidator),

			//Node
			entry(ACCOUNT_GET_INFO, nodeHandlers::accountGetInfo),
			entry(ACCOUNT_SUBMIT_TRANSACTION_SINGLE_STEP, nodeHandlers::accountSubmitTransactionSingleStep),
			entry(API_GET_CONFIGURATION, nodeHandlers::apiGetConfiguration),
			entry(API_GET_DATA, nodeHandlers::apiGetData),
			entry(BFT_GET_CONFIGURATION, nodeHandlers::bftGetConfiguration),
			entry(BFT_GET_DATA, nodeHandlers::bftGetData),
			entry(CHECKPOINTS_GET_CHECKPOINTS, nodeHandlers::checkpointsGetCheckpoints),
			entry(GET_TRANSACTIONS, nodeHandlers::getTransactions),
			entry(LEDGER_GET_LATEST_EPOCH_PROOF, nodeHandlers::ledgerGetLatestEpochProof),
			entry(LEDGER_GET_LATEST_PROOF, nodeHandlers::ledgerGetLatestProof),
			entry(MEMPOOL_GET_CONFIGURATION, nodeHandlers::mempoolGetConfiguration),
			entry(MEMPOOL_GET_DATA, nodeHandlers::mempoolGetData),
			entry(NETWORKING_GET_ADDRESS_BOOK, nodeHandlers::networkingGetAddressBook),
			entry(NETWORKING_GET_CONFIGURATION, nodeHandlers::networkingGetConfiguration),
			entry(NETWORKING_GET_DATA, nodeHandlers::networkingGetData),
			entry(NETWORKING_GET_PEERS, nodeHandlers::networkingGetPeers),
			entry(RADIX_ENGINE_GET_CONFIGURATION, nodeHandlers::radixEngineGetConfiguration),
			entry(RADIX_ENGINE_GET_DATA, nodeHandlers::radixEngineGetData),
			entry(SYNC_GET_CONFIGURATION, nodeHandlers::syncGetConfiguration),
			entry(SYNC_GET_DATA, nodeHandlers::syncGetData),
			entry(VALIDATION_GET_CURRENT_EPOCH_DATA, nodeHandlers::validationGetCurrentEpochData),
			entry(VALIDATION_GET_NODE_INFO, nodeHandlers::validationGetNodeInfo)
		);
	}
}
