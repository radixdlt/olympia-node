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

import org.radix.Radix;

import com.google.inject.Inject;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.counters.SystemCounters.CounterType;
import com.radixdlt.middleware2.InfoSupplier;

import java.util.List;
import java.util.Map;

//TODO: finish it
public class MetricsService {
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
		CounterType.MESSAGES_INBOUND_PROCESSED,
		CounterType.MESSAGES_INBOUND_DISCARDED,
		CounterType.MESSAGES_INBOUND_BADSIGNATURE,
		CounterType.MESSAGES_INBOUND_RECEIVED,
		CounterType.MESSAGES_OUTBOUND_PROCESSED,
		CounterType.MESSAGES_OUTBOUND_ABORTED,
		CounterType.MESSAGES_OUTBOUND_PENDING,
		CounterType.MESSAGES_OUTBOUND_SENT,
		CounterType.PERSISTENCE_ATOM_LOG_WRITE_BYTES,
		CounterType.PERSISTENCE_ATOM_LOG_WRITE_COMPRESSED,
		CounterType.TIME_DURATION
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

		appendCounterExtended(
			builder,
			prepareNodeInfo(),
			"nodeinfo",
			"Special metric used to convey information about the current node using labels. Value will always be 0.",
			0.0
		);
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

	private void appendCounter(StringBuilder builder, String name, Number value) {
		appendCounterExtended(builder, name, name, name, value);
	}

	private void appendCounterExtended(StringBuilder builder, String name, String type, String help, Number value) {
		builder
			.append("# HELP ").append(help).append('\n')
			.append("# TYPE ").append(type).append(' ').append(COUNTER).append('\n')
			.append(name).append(' ').append(value.doubleValue()).append('\n');
	}
}
