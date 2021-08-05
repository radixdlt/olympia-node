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

package com.radixdlt.api.service;

import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.Self;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.networks.Addressing;
import com.radixdlt.systeminfo.InMemorySystemInfo;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.inject.Inject;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.counters.SystemCounters.CounterType;
import com.radixdlt.middleware2.InfoSupplier;
import com.radixdlt.utils.UInt384;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.management.InstanceNotFoundException;
import javax.management.MBeanServerConnection;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.management.ReflectionException;
import javax.management.openmbean.CompositeDataSupport;

import static org.radix.Radix.SYSTEM_VERSION_KEY;
import static org.radix.Radix.VERSION_STRING_KEY;

import static com.radixdlt.api.service.MetricsService.JmxMetric.jmxMetric;

public class MetricsService {
	private static final Logger log = LogManager.getLogger();

	private static final List<CounterType> EXPORT_LIST = List.of(
		CounterType.BFT_CONSENSUS_EVENTS,
		CounterType.BFT_INDIRECT_PARENT,
		CounterType.BFT_PROCESSED,
		CounterType.BFT_PROPOSALS_MADE,
		CounterType.BFT_REJECTED,
		CounterType.BFT_STATE_VERSION,
		CounterType.BFT_SYNC_REQUESTS_SENT,
		CounterType.BFT_SYNC_REQUEST_TIMEOUTS,
		CounterType.BFT_TIMED_OUT_VIEWS,
		CounterType.BFT_TIMEOUT,
		CounterType.BFT_TIMEOUT_QUORUMS,
		CounterType.BFT_VERTEX_STORE_FORKS,
		CounterType.BFT_VERTEX_STORE_REBUILDS,
		CounterType.BFT_VERTEX_STORE_SIZE,
		CounterType.BFT_VOTE_QUORUMS,
		CounterType.COUNT_APIDB_BALANCE_BYTES_READ,
		CounterType.COUNT_APIDB_BALANCE_BYTES_WRITE,
		CounterType.COUNT_APIDB_BALANCE_READ,
		CounterType.COUNT_APIDB_BALANCE_TOTAL,
		CounterType.COUNT_APIDB_BALANCE_WRITE,
		CounterType.COUNT_APIDB_FLUSH_COUNT,
		CounterType.COUNT_APIDB_QUEUE_SIZE,
		CounterType.COUNT_APIDB_TOKEN_BYTES_READ,
		CounterType.COUNT_APIDB_TOKEN_BYTES_WRITE,
		CounterType.COUNT_APIDB_TOKEN_READ,
		CounterType.COUNT_APIDB_TOKEN_TOTAL,
		CounterType.COUNT_APIDB_TOKEN_WRITE,
		CounterType.COUNT_APIDB_TRANSACTION_BYTES_READ,
		CounterType.COUNT_APIDB_TRANSACTION_BYTES_WRITE,
		CounterType.COUNT_APIDB_TRANSACTION_READ,
		CounterType.COUNT_APIDB_TRANSACTION_TOTAL,
		CounterType.COUNT_APIDB_TRANSACTION_WRITE,
		CounterType.COUNT_BDB_ADDRESS_BOOK_BYTES_READ,
		CounterType.COUNT_BDB_ADDRESS_BOOK_BYTES_WRITE,
		CounterType.COUNT_BDB_ADDRESS_BOOK_DELETES,
		CounterType.COUNT_BDB_ADDRESS_BOOK_TOTAL,
		CounterType.COUNT_BDB_HEADER_BYTES_WRITE,
		CounterType.COUNT_BDB_LEDGER_BYTES_READ,
		CounterType.COUNT_BDB_LEDGER_BYTES_WRITE,
		CounterType.COUNT_BDB_LEDGER_COMMIT,
		CounterType.COUNT_BDB_LEDGER_CONTAINS,
		CounterType.COUNT_BDB_LEDGER_CONTAINS_TX,
		CounterType.COUNT_BDB_LEDGER_CREATE_TX,
		CounterType.COUNT_BDB_LEDGER_DELETES,
		CounterType.COUNT_BDB_LEDGER_ENTRIES,
		CounterType.COUNT_BDB_LEDGER_GET,
		CounterType.COUNT_BDB_LEDGER_GET_FIRST,
		CounterType.COUNT_BDB_LEDGER_GET_LAST,
		CounterType.COUNT_BDB_LEDGER_GET_NEXT,
		CounterType.COUNT_BDB_LEDGER_GET_PREV,
		CounterType.COUNT_BDB_LEDGER_LAST_COMMITTED,
		CounterType.COUNT_BDB_LEDGER_LAST_VERTEX,
		CounterType.COUNT_BDB_LEDGER_PROOFS_ADDED,
		CounterType.COUNT_BDB_LEDGER_PROOFS_REMOVED,
		CounterType.COUNT_BDB_LEDGER_SAVE,
		CounterType.COUNT_BDB_LEDGER_SEARCH,
		CounterType.COUNT_BDB_LEDGER_STORE,
		CounterType.COUNT_BDB_LEDGER_TOTAL,
		CounterType.COUNT_BDB_SAFETY_STATE_BYTES_READ,
		CounterType.COUNT_BDB_SAFETY_STATE_BYTES_WRITE,
		CounterType.COUNT_BDB_SAFETY_STATE_TOTAL,
		CounterType.ELAPSED_APIDB_BALANCE_READ,
		CounterType.ELAPSED_APIDB_BALANCE_WRITE,
		CounterType.ELAPSED_APIDB_FLUSH_TIME,
		CounterType.ELAPSED_APIDB_TOKEN_READ,
		CounterType.ELAPSED_APIDB_TOKEN_WRITE,
		CounterType.ELAPSED_APIDB_TRANSACTION_READ,
		CounterType.ELAPSED_APIDB_TRANSACTION_WRITE,
		CounterType.ELAPSED_BDB_ADDRESS_BOOK,
		CounterType.ELAPSED_BDB_LEDGER_COMMIT,
		CounterType.ELAPSED_BDB_LEDGER_CONTAINS,
		CounterType.ELAPSED_BDB_LEDGER_CONTAINS_TX,
		CounterType.ELAPSED_BDB_LEDGER_CREATE_TX,
		CounterType.ELAPSED_BDB_LEDGER_ENTRIES,
		CounterType.ELAPSED_BDB_LEDGER_GET,
		CounterType.ELAPSED_BDB_LEDGER_GET_FIRST,
		CounterType.ELAPSED_BDB_LEDGER_GET_LAST,
		CounterType.ELAPSED_BDB_LEDGER_LAST_COMMITTED,
		CounterType.ELAPSED_BDB_LEDGER_LAST_VERTEX,
		CounterType.ELAPSED_BDB_LEDGER_SAVE,
		CounterType.ELAPSED_BDB_LEDGER_SEARCH,
		CounterType.ELAPSED_BDB_LEDGER_STORE,
		CounterType.ELAPSED_BDB_LEDGER_TOTAL,
		CounterType.ELAPSED_BDB_SAFETY_STATE,
		CounterType.EPOCH_MANAGER_QUEUED_CONSENSUS_EVENTS,
		CounterType.HASHED_BYTES,
		CounterType.LEDGER_BFT_COMMANDS_PROCESSED,
		CounterType.LEDGER_STATE_VERSION,
		CounterType.LEDGER_SYNC_COMMANDS_PROCESSED,
		CounterType.MEMPOOL_ADD_SUCCESS,
		CounterType.MEMPOOL_COUNT,
		CounterType.MEMPOOL_ERRORS_CONFLICT,
		CounterType.MEMPOOL_ERRORS_HOOK,
		CounterType.MEMPOOL_ERRORS_OTHER,
		CounterType.MEMPOOL_MAXCOUNT,
		CounterType.MEMPOOL_PROPOSED_TRANSACTION,
		CounterType.MEMPOOL_RELAYER_SENT_COUNT,
		CounterType.MESSAGES_INBOUND_DISCARDED,
		CounterType.MESSAGES_INBOUND_PROCESSED,
		CounterType.MESSAGES_INBOUND_RECEIVED,
		CounterType.MESSAGES_OUTBOUND_ABORTED,
		CounterType.MESSAGES_OUTBOUND_PENDING,
		CounterType.MESSAGES_OUTBOUND_PROCESSED,
		CounterType.MESSAGES_OUTBOUND_SENT,
		CounterType.NETWORKING_RECEIVED_BYTES,
		CounterType.NETWORKING_SENT_BYTES,
		CounterType.NETWORKING_TCP_CLOSED,
		CounterType.NETWORKING_TCP_DROPPED_MESSAGES,
		CounterType.NETWORKING_TCP_IN_OPENED,
		CounterType.NETWORKING_TCP_OUT_OPENED,
		CounterType.NETWORKING_UDP_DROPPED_MESSAGES,
		CounterType.PERSISTENCE_ATOM_LOG_WRITE_BYTES,
		CounterType.PERSISTENCE_ATOM_LOG_WRITE_COMPRESSED,
		CounterType.PERSISTENCE_VERTEX_STORE_SAVES,
		CounterType.PERSISTENCE_SAFETY_STORE_SAVES,
		CounterType.RADIX_ENGINE_SYSTEM_TRANSACTIONS,
		CounterType.RADIX_ENGINE_USER_TRANSACTIONS,
		CounterType.SIGNATURES_SIGNED,
		CounterType.SIGNATURES_VERIFIED,
		CounterType.STARTUP_TIME_MS,
		CounterType.SYNC_INVALID_COMMANDS_RECEIVED,
		CounterType.SYNC_LAST_READ_MILLIS,
		CounterType.SYNC_PROCESSED,
		CounterType.SYNC_REMOTE_REQUESTS_PROCESSED,
		CounterType.SYNC_TARGET_CURRENT_DIFF,
		CounterType.SYNC_TARGET_STATE_VERSION,
		CounterType.TIME_DURATION
	);

	private static final List<JmxMetric> JMX_METRICS = List.of(
		jmxMetric("java.lang:type=MemoryPool,name=G1 Eden Space", "Usage"),
		jmxMetric("java.lang:type=MemoryPool,name=G1 Survivor Space", "Usage"),
		jmxMetric("java.lang:type=MemoryPool,name=G1 Old Gen", "Usage"),
		jmxMetric("java.lang:type=MemoryPool,name=Metaspace", "Usage"),
		jmxMetric("java.lang:type=GarbageCollector,name=G1 Old Generation", "Usage"),
		jmxMetric("java.lang:type=GarbageCollector,name=G1 Young Generation", "Usage"),
		jmxMetric("java.lang:type=OperatingSystem", "SystemCpuLoad", "ProcessCpuLoad", "SystemLoadAverage"),
		jmxMetric("java.lang:type=Threading", "ThreadCount", "DaemonThreadCount"),
		jmxMetric("java.lang:type=Memory", "HeapMemoryUsage", "NonHeapMemoryUsage"),
		jmxMetric("java.lang:type=ClassLoading", "LoadedClassCount")
	);

	private static final String COUNTER = "counter";
	private static final String COUNTER_PREFIX = "info_counters_";

	private final SystemCounters systemCounters;
	private final InfoSupplier infoSupplier;
	private final SystemConfigService systemConfigService;
	private final NetworkingService networkingService;
	private final AccountInfoService accountInfoService;
	private final ValidatorInfoService validatorInfoService;
	private final NetworkInfoService networkInfoService;
	private final Addressing addressing;
	private final InMemorySystemInfo inMemorySystemInfo;
	private final BFTNode self;

	@Inject
	public MetricsService(
		SystemCounters systemCounters,
		InfoSupplier infoSupplier,
		SystemConfigService systemConfigService,
		NetworkingService networkingService,
		AccountInfoService accountInfoService,
		ValidatorInfoService validatorInfoService,
		NetworkInfoService networkInfoService,
		InMemorySystemInfo inMemorySystemInfo,
		@Self BFTNode self,
		Addressing addressing
	) {
		this.systemCounters = systemCounters;
		this.infoSupplier = infoSupplier;
		this.systemConfigService = systemConfigService;
		this.networkingService = networkingService;
		this.accountInfoService = accountInfoService;
		this.validatorInfoService = validatorInfoService;
		this.networkInfoService = networkInfoService;
		this.inMemorySystemInfo = inMemorySystemInfo;
		this.self = self;
		this.addressing = addressing;
	}

	public String getMetrics() {
		var builder = new StringBuilder();

		exportCounters(builder);
		exportSystemInfo(builder);

		return builder.append('\n').toString();
	}

	private void exportSystemInfo(StringBuilder builder) {
		var snapshot = infoSupplier.getInfo();

		appendCounter(builder, "info_configuration_pacemakermaxexponent", pacemakerMaxExponent(snapshot));
		appendCounter(builder, "info_epochmanager_currentview_view", currentView(snapshot));
		appendCounter(builder, "info_epochmanager_currentview_epoch", currentEpoch(snapshot));
		appendCounter(builder, "total_peers", networkingService.getPeersCount());
		var totalValidators = inMemorySystemInfo.getEpochProof().getNextValidatorSet().orElseThrow().getValidators().size();
		appendCounter(builder, "total_validators", totalValidators);

		appendCounter(builder, "balance_xrd", getXrdBalance());
		appendCounter(builder, "validator_total_stake", getTotalStake());

		appendJMXCounters(builder);

		appendCounterExtended(
			builder,
			prepareNodeInfo(),
			"nodeinfo",
			"Special metric used to convey information about the current node using labels. Value will always be 0.",
			0.0
		);
	}

	private UInt384 getTotalStake() {
		var stakeData = validatorInfoService.getValidatorStakeData(self.getKey());
		return UInt384.from(stakeData.getTotalStake());
	}

	private UInt384 getXrdBalance() {
		return accountInfoService.getMyBalances().getOrDefault(REAddr.ofNativeToken(), UInt384.ZERO);
	}

	@SuppressWarnings("unchecked")
	private Number currentView(Map<String, Map<String, Object>> snapshot) {
		var currentView = (Map<String, Object>) snapshot.get("epochManager").get("currentView");
		return (Number) currentView.get("view");
	}

	@SuppressWarnings("unchecked")
	private Number currentEpoch(Map<String, Map<String, Object>> snapshot) {
		var currentView = (Map<String, Object>) snapshot.get("epochManager").get("currentView");
		return (Number) currentView.get("epoch");
	}

	private Number pacemakerMaxExponent(Map<String, Map<String, Object>> snapshot) {
		return (Number) snapshot.get("configuration").get("pacemakerMaxExponent");
	}

	private String prepareNodeInfo() {
		var builder = new StringBuilder("nodeinfo{");

		addEndpontStatuses(builder);
		appendField(builder, "owner_address", addressing.forAccounts().of(REAddr.ofPubKeyAccount(self.getKey())));
		appendField(builder, "validator_registered", validatorInfoService.getNextEpochRegisteredFlag(self.getKey()).isRegistered());
		addBranchAndCommit(builder);
		addValidatorAddress(builder);
		addAccumulatorState(builder);
		appendField(builder, "health", networkInfoService.nodeStatus().name());
		appendField(builder, "key", self.getKey().toHex());

		return builder.append("}").toString();
	}

	private void addValidatorAddress(StringBuilder builder) {
		appendField(builder, "own_validator_address", addressing.forValidators().of(self.getKey()));
		var validatorSet = inMemorySystemInfo.getEpochProof().getNextValidatorSet().orElseThrow();
		var inSet = validatorSet.containsNode(self);
		appendField(builder, "is_in_validator_set", inSet);
	}

	private void addBranchAndCommit(StringBuilder builder) {
		var branchAndCommit = infoSupplier.getInfo().get(SYSTEM_VERSION_KEY).get(VERSION_STRING_KEY);
		appendField(builder, "branch_and_commit", branchAndCommit);
	}

	private void addAccumulatorState(StringBuilder builder) {
		var accumulatorState = systemConfigService.accumulatorState();

		appendField(
			builder,
			"version_accumulator",
			accumulatorState.getStateVersion() + "/" + accumulatorState.getAccumulatorHash().toString()
		);
	}

	private void addEndpontStatuses(StringBuilder builder) {
		systemConfigService.withEndpointStatuses(status ->
													 appendField(builder, status.name() + "_enabled", status.enabled()));
	}

	private void appendField(StringBuilder builder, String name, Object value) {
		builder.append(name).append("=\"").append(value).append("\",");
	}

	private void exportCounters(StringBuilder builder) {
		EXPORT_LIST.forEach(counterType -> generateCounterEntry(counterType, builder));

		var uptime = validatorInfoService.getUptime(self.getKey());
		appendCounter(builder, COUNTER_PREFIX + "radix_engine_cur_epoch_completed_proposals", uptime.getProposalsCompleted());
		appendCounter(builder, COUNTER_PREFIX + "radix_engine_cur_epoch_missed_proposals", uptime.getProposalsMissed());
	}

	private void generateCounterEntry(CounterType counterType, StringBuilder builder) {
		var name = COUNTER_PREFIX + counterType.jsonPath().replace('.', '_');

		long value = systemCounters.get(counterType);

		appendCounter(builder, name, value);
	}

	private static void appendCounter(StringBuilder builder, String name, Number value) {
		appendCounterExtended(builder, name, name, name, value.doubleValue());
	}

	private static void appendCounter(StringBuilder builder, String name, UInt384 value) {
		appendCounterExtended(builder, name, name, name, value.toString() + ".0");
	}

	private static void appendCounterExtended(StringBuilder builder, String name, String type, String help, Object value) {
		builder
			.append("# HELP ").append(help).append('\n')
			.append("# TYPE ").append(type).append(' ').append(COUNTER).append('\n')
			.append(name).append(' ').append(value).append('\n');
	}

	static class JmxMetric {
		private final String objectNameString;
		private final String[] metricAttributes;

		private JmxMetric(String objectNameString, String[] metricAttributes) {
			this.objectNameString = objectNameString;
			this.metricAttributes = metricAttributes;
		}

		static JmxMetric jmxMetric(String objectName, String... attributes) {
			return new JmxMetric(objectName, attributes);
		}

		void readCounter(MBeanServerConnection connection, StringBuilder builder) {
			try {
				var objectName = connection.queryNames(new ObjectName(objectNameString), null)
					.iterator()
					.next();

				var attributes = connection.getAttributes(objectName, metricAttributes).asList();

				for (var attribute : attributes) {
					var name = attribute.getName();

					if (name.equals("Usage")) {
						name = objectName.getKeyProperty("name");
					}

					var outName = name.toLowerCase(Locale.US)
						.replace('.', '_')
						.replace(' ', '_');

					// this might break if more beans are parsed
					if (attribute.getValue() instanceof CompositeDataSupport) {
						var cds = (CompositeDataSupport) attribute.getValue();

						appendCounter(builder, outName + "_init", (Number) cds.get("init"));
						appendCounter(builder, outName + "_max", (Number) cds.get("max"));
						appendCounter(builder, outName + "_committed", (Number) cds.get("committed"));
						appendCounter(builder, outName + "_used", (Number) cds.get("used"));
					} else {
						appendCounter(builder, outName, (Number) attribute.getValue());
					}
				}
			} catch (InstanceNotFoundException | ReflectionException | IOException | MalformedObjectNameException e) {
				log.error("Error while retrieving JMX metric " + objectNameString, e);
			}
		}
	}

	private void appendJMXCounters(StringBuilder builder) {
		var connection = ManagementFactory.getPlatformMBeanServer();
		JMX_METRICS.forEach(metric -> metric.readCounter(connection, builder));
	}
}
