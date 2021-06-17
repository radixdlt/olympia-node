/*
 * (C) Copyright 2021 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 */

package com.radixdlt.api.service;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.radix.Radix;

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

import static com.radixdlt.api.service.MetricsService.JmxMetric.jmxMetric;

//TODO: finish it
public class MetricsService {
	private static final Logger log = LogManager.getLogger();

	private static final List<CounterType> EXPORT_LIST = List.of(
		CounterType.COUNT_APIDB_TOKEN_TOTAL,
		CounterType.COUNT_APIDB_TOKEN_READ,
		CounterType.COUNT_APIDB_TOKEN_BYTES_READ,
		CounterType.COUNT_APIDB_TOKEN_BYTES_WRITE,
		CounterType.COUNT_APIDB_TOKEN_WRITE,
		CounterType.EPOCH_MANAGER_QUEUED_CONSENSUS_EVENTS,
		CounterType.HASHED_BYTES,
		CounterType.NETWORKING_RECEIVED_BYTES,
		CounterType.NETWORKING_TCP_OUT_OPENED,
		CounterType.NETWORKING_TCP_DROPPED_MESSAGES,
		CounterType.NETWORKING_TCP_IN_OPENED,
		CounterType.NETWORKING_TCP_CLOSED,
		CounterType.NETWORKING_UDP_DROPPED_MESSAGES,
		CounterType.NETWORKING_SENT_BYTES,
		CounterType.SYNC_PROCESSED,
		CounterType.SYNC_TARGET_STATE_VERSION,
		CounterType.SYNC_REMOTE_REQUESTS_PROCESSED,
		CounterType.SYNC_INVALID_COMMANDS_RECEIVED,
		CounterType.SYNC_LAST_READ_MILLIS,
		CounterType.SYNC_TARGET_CURRENT_DIFF,
		CounterType.SIGNATURES_VERIFIED,
		CounterType.SIGNATURES_SIGNED,
		CounterType.ELAPSED_BDB_LEDGER_CONTAINS_TX,
		CounterType.ELAPSED_BDB_LEDGER_COMMIT,
		CounterType.ELAPSED_BDB_LEDGER_SAVE,
		CounterType.ELAPSED_BDB_LEDGER_LAST_VERTEX,
		CounterType.ELAPSED_BDB_LEDGER_STORE,
		CounterType.ELAPSED_BDB_LEDGER_CREATE_TX,
		CounterType.ELAPSED_BDB_LEDGER_CONTAINS,
		CounterType.ELAPSED_BDB_LEDGER_ENTRIES,
		CounterType.ELAPSED_BDB_LEDGER_GET_LAST,
		CounterType.ELAPSED_BDB_LEDGER_SEARCH,
		CounterType.ELAPSED_BDB_LEDGER_TOTAL,
		CounterType.ELAPSED_BDB_LEDGER_GET,
		CounterType.ELAPSED_BDB_LEDGER_LAST_COMMITTED,
		CounterType.ELAPSED_BDB_LEDGER_GET_FIRST,
		CounterType.ELAPSED_BDB_ADDRESS_BOOK,
		CounterType.ELAPSED_BDB_SAFETY_STATE,
		CounterType.ELAPSED_APIDB_BALANCE_READ,
		CounterType.ELAPSED_APIDB_BALANCE_WRITE,
		CounterType.ELAPSED_APIDB_FLUSH_TIME,
		CounterType.ELAPSED_APIDB_TRANSACTION_READ,
		CounterType.ELAPSED_APIDB_TRANSACTION_WRITE,
		CounterType.ELAPSED_APIDB_TOKEN_READ,
		CounterType.ELAPSED_APIDB_TOKEN_WRITE,
		CounterType.BFT_STATE_VERSION,
		CounterType.BFT_VOTE_QUORUMS,
		CounterType.BFT_REJECTED,
		CounterType.BFT_VERTEX_STORE_REBUILDS,
		CounterType.BFT_VERTEX_STORE_FORKS,
		CounterType.BFT_SYNC_REQUEST_TIMEOUTS,
		CounterType.BFT_SYNC_REQUESTS_SENT,
		CounterType.BFT_TIMEOUT,
		CounterType.BFT_VERTEX_STORE_SIZE,
		CounterType.BFT_PROCESSED,
		CounterType.BFT_CONSENSUS_EVENTS,
		CounterType.BFT_INDIRECT_PARENT,
		CounterType.BFT_PROPOSALS_MADE,
		CounterType.BFT_TIMED_OUT_VIEWS,
		CounterType.BFT_TIMEOUT_QUORUMS,
		CounterType.STARTUP_TIME_MS,
		CounterType.LEDGER_STATE_VERSION,
		CounterType.LEDGER_SYNC_COMMANDS_PROCESSED,
		CounterType.LEDGER_BFT_COMMANDS_PROCESSED,
		CounterType.MEMPOOL_COUNT,
		CounterType.MEMPOOL_MAXCOUNT,
		CounterType.MEMPOOL_RELAYER_SENT_COUNT,
		CounterType.MEMPOOL_ADD_SUCCESS,
		CounterType.MEMPOOL_PROPOSED_TRANSACTION,
		CounterType.MEMPOOL_ERRORS_HOOK,
		CounterType.MEMPOOL_ERRORS_CONFLICT,
		CounterType.MEMPOOL_ERRORS_OTHER,
		CounterType.RADIX_ENGINE_USER_TRANSACTIONS,
		CounterType.RADIX_ENGINE_SYSTEM_TRANSACTIONS,
		CounterType.MESSAGES_INBOUND_PROCESSED,
		CounterType.MESSAGES_INBOUND_DISCARDED,
		CounterType.MESSAGES_INBOUND_RECEIVED,
		CounterType.MESSAGES_OUTBOUND_PROCESSED,
		CounterType.MESSAGES_OUTBOUND_ABORTED,
		CounterType.MESSAGES_OUTBOUND_PENDING,
		CounterType.MESSAGES_OUTBOUND_SENT,
		CounterType.PERSISTENCE_ATOM_LOG_WRITE_BYTES,
		CounterType.PERSISTENCE_ATOM_LOG_WRITE_COMPRESSED,
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
	private final ValidatorInfoService validatorInfoService;
	private final AccountInfoService accountInfoService;

	@Inject
	public MetricsService(
		SystemCounters systemCounters,
		InfoSupplier infoSupplier,
		SystemConfigService systemConfigService,
		ValidatorInfoService validatorInfoService,
		AccountInfoService accountInfoService
	) {
		this.systemCounters = systemCounters;
		this.infoSupplier = infoSupplier;
		this.systemConfigService = systemConfigService;
		this.validatorInfoService = validatorInfoService;
		this.accountInfoService = accountInfoService;
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
		appendCounter(builder, "info_system_version_system_version_agent_version", agentVersion(snapshot));
		appendCounter(builder, "info_system_version_system_version_protocol_version", protocolVersion(snapshot));

		appendCounter(builder, "info_epochmanager_currentview_view", currentView(snapshot));
		appendCounter(builder, "info_epochmanager_currentview_epoch", currentEpoch(snapshot));
		appendCounter(builder, "total_peers", systemConfigService.getNetworkingPeersCount());
		appendCounter(builder, "total_validators", validatorInfoService.getValidatorsCount());

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
		return UInt384.from(accountInfoService.getValidatorStakeData().getTotalStake());
	}

	private UInt384 getXrdBalance() {
		return accountInfoService.getMyBalances()
			.stream()
			.filter(e -> e.getKey().isNativeToken())
			.map(Map.Entry::getValue)
			.findAny()
			.orElse(UInt384.ZERO);
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

	private Number protocolVersion(Map<String, Map<String, Object>> snapshot) {
		return (Number) snapshot.get(Radix.SYSTEM_VERSION_KEY).get("protocol_version");
	}

	private Number agentVersion(Map<String, Map<String, Object>> snapshot) {
		return (Number) snapshot.get(Radix.SYSTEM_VERSION_KEY).get("agent_version");
	}

	private Number pacemakerMaxExponent(Map<String, Map<String, Object>> snapshot) {
		return (Number) snapshot.get("configuration").get("pacemakerMaxExponent");
	}

	private String prepareNodeInfo() {
		var builder = new StringBuilder("nodeinfo{");

		addEndpontStatuses(builder);
		appendField(builder, "owner_address", accountInfoService.getOwnAddress());
		appendField(builder, "validator_registered", accountInfoService.getValidatorInfoDetails().isRegistered());
		addBranchAndCommit(builder);
		addValidatorAddress(builder);
		addAccumulatorState(builder);
		appendField(builder, "key", accountInfoService.getOwnPubKey().toHex());

		return builder.append("}").toString();
	}

	private void addValidatorAddress(StringBuilder builder) {
		var validatorAddress = accountInfoService.getValidatorAddress();
		appendField(builder, "own_validator_address", validatorAddress);

		var inSet = validatorInfoService.getAllValidators()
			.stream()
			.anyMatch(v -> v.getValidatorAddress().equals(validatorAddress));
		appendField(builder, "is_in_validator_set", inSet);
	}

	private void addBranchAndCommit(StringBuilder builder) {
		var branchAndCommit = ((String) infoSupplier.getInfo().get(Radix.SYSTEM_VERSION_KEY).get("display"))
			.substring(5);

		if (branchAndCommit.contains("-dirty")) {
			branchAndCommit = branchAndCommit.substring(0, branchAndCommit.length() - 6);
		}

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
