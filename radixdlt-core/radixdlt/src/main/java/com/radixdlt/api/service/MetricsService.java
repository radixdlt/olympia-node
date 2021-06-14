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

import com.google.inject.Inject;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.counters.SystemCounters.CounterType;

import java.util.List;

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
		CounterType.PERSISTENCE_ATOM_LOG_WRITE_COMPRESSED
		//CounterType.TIME_DURATION
	);
	private static final String COUNTER = "counter";
	private static final String COUNTER_PREFIX = "info_counters_";

	private final SystemCounters systemCounters;
	private final NetworkInfoService networkInfoService;

	@Inject
	public MetricsService(
		SystemCounters systemCounters,
		NetworkInfoService networkInfoService
	) {
		this.systemCounters = systemCounters;
		this.networkInfoService = networkInfoService;
	}

	public String getMetrics() {
		var builder = new StringBuilder();

		exportCounters(builder);

		return builder.append('\n').toString();
	}

	private void exportCounters(StringBuilder builder) {
		EXPORT_LIST.forEach(counterType -> generateCounterEntry(counterType, builder));
	}

	private void generateCounterEntry(CounterType counterType, StringBuilder builder) {
		var name = COUNTER_PREFIX + counterType.jsonPath().replace('.', '_');

		builder
			.append("# HELP ").append(name).append('\n')
			.append("# TYPE ").append(name).append(' ').append(COUNTER).append('\n')
			.append(name).append(' ').append(systemCounters.get(counterType)).append(".0\n");
	}
}
