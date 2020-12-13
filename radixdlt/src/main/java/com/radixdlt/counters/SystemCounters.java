/*
 * (C) Copyright 2020 Radix DLT Ltd
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

package com.radixdlt.counters;

import java.util.Map;

/**
 * System counters interface.
 */
public interface SystemCounters {

	enum CounterType {
		// Please keep these sorted

		BFT_INDIRECT_PARENT("bft.indirect_parent"),
		BFT_PROCESSED("bft.processed"),
		BFT_PROPOSALS_MADE("bft.proposals_made"),
		/** Number of proposals rejected. */
		BFT_REJECTED("bft.rejected"),
		/** Number of times a view-timeout message was broadcast. */
		BFT_TIMEOUT("bft.timeout"),
		/** Number of views that timed out. Rescheduled timeouts of the same view are not counted */
		BFT_TIMED_OUT_VIEWS("bft.timed_out_views"),
		/** Number of view-timeout quorums formed. */
		BFT_TIMEOUT_QUORUMS("bft.timeout_quorums"),
		BFT_STATE_VERSION("bft.state_version"),
		BFT_VERTEX_STORE_SIZE("bft.vertex_store_size"),
		BFT_VERTEX_STORE_FORKS("bft.vertex_store_forks"),
		BFT_VERTEX_STORE_REBUILDS("bft.vertex_store_rebuilds"),
		/** Number of vote quorums formed. */
		BFT_VOTE_QUORUMS("bft.vote_quorums"),
		BFT_SYNC_REQUESTS_SENT("bft.sync.requests_sent"),
		BFT_SYNC_REQUEST_TIMEOUTS("bft.sync.request_timeouts"),

		// Total elapsed time for database access, in microseconds
		ELAPSED_BDB_ADDRESS_BOOK("elapsed.bdb.address_book"),
		ELAPSED_BDB_LEDGER_COMMIT("elapsed.bdb.ledger.commit"),
		ELAPSED_BDB_LEDGER_CREATE_TX("elapsed.bdb.ledger.create_tx"),
		ELAPSED_BDB_LEDGER_CONTAINS("elapsed.bdb.ledger.contains"),
		ELAPSED_BDB_LEDGER_CONTAINS_TX("elapsed.bdb.ledger.contains_tx"),
		ELAPSED_BDB_LEDGER_ENTRIES("elapsed.bdb.ledger.entries"),
		ELAPSED_BDB_LEDGER_GET("elapsed.bdb.ledger.get"),
		ELAPSED_BDB_LEDGER_GET_FIRST("elapsed.bdb.ledger.get_first"),
		ELAPSED_BDB_LEDGER_GET_LAST("elapsed.bdb.ledger.get_last"),
		ELAPSED_BDB_LEDGER_GET_NEXT("elapsed.bdb.ledger.get_next"),
		ELAPSED_BDB_LEDGER_GET_PREV("elapsed.bdb.ledger.get_prev"),
		ELAPSED_BDB_LEDGER_STORE("elapsed.bdb.ledger.store"),
		ELAPSED_BDB_LEDGER_LAST_COMMITTED("elapsed.bdb.ledger.last_committed"),
		ELAPSED_BDB_LEDGER_LAST_VERTEX("elapsed.bdb.ledger.last_vertex"),
		ELAPSED_BDB_LEDGER_SAVE("elapsed.bdb.ledger.save"),
		ELAPSED_BDB_LEDGER_SAVE_TX("elapsed.bdb.ledger.save_tx"),
		ELAPSED_BDB_LEDGER_SEARCH("elapsed.bdb.ledger.search"),
		ELAPSED_BDB_LEDGER_TOTAL("elapsed.bdb.ledger.total"),
		ELAPSED_BDB_SAFETY_STATE("elapsed.bdb.safety_state"),

		PERSISTENCE_VERTEX_STORE_SAVES("persistence.vertex_store_saves"),
		PERSISTENCE_SAFETY_STORE_SAVES("persistence.safety_store_saves"),

		EPOCH_MANAGER_QUEUED_CONSENSUS_EVENTS("epoch_manager.queued_consensus_events"),

		HASHED_BYTES("hashed.bytes"),

		LEDGER_PROCESSED("ledger.processed"),
		LEDGER_STATE_VERSION("ledger.state_version"),

		SYNC_LAST_READ_MILLIS("sync.last_read_millis"),
		SYNC_INVALID_COMMANDS_RECEIVED("sync.invalid_commands_received"),
		SYNC_PROCESSED("sync.processed"),
		SYNC_TARGET_STATE_VERSION("sync.target_state_version"),

		MEMPOOL_COUNT("mempool.count"),
		MEMPOOL_MAXCOUNT("mempool.maxcount"),

		MESSAGES_INBOUND_BADSIGNATURE("messages.inbound.badsignature"),
		MESSAGES_INBOUND_DISCARDED("messages.inbound.discarded"),
		MESSAGES_INBOUND_PENDING("messages.inbound.pending"),
		MESSAGES_INBOUND_PROCESSED("messages.inbound.processed"),
		MESSAGES_INBOUND_RECEIVED("messages.inbound.received"),
		MESSAGES_OUTBOUND_ABORTED("messages.outbound.aborted"),
		MESSAGES_OUTBOUND_PENDING("messages.outbound.pending"),
		MESSAGES_OUTBOUND_PROCESSED("messages.outbound.processed"),
		MESSAGES_OUTBOUND_SENT("messages.outbound.sent"),

		NETWORKING_TCP_OPENED("networking.tcp.opened"),
		NETWORKING_TCP_CLOSED("networking.tcp.closed"),
		NETWORKING_SENT_BYTES("networking.sent_bytes"),
		NETWORKING_RECEIVED_BYTES("networking.received_bytes"),

		NETWORKING_DROPPED_ERROR_RESPONSES("networking.dropped_error_responses"),

		SIGNATURES_SIGNED("signatures.signed"),
		SIGNATURES_VERIFIED("signatures.verified");

		private final String jsonPath;

		CounterType(String jsonPath) {
			this.jsonPath = jsonPath;
		}

		public String jsonPath() {
			return this.jsonPath;
		}
	}

	/**
	 * Increments the specified counter, returning the new value.
	 *
	 * @param counterType The counter to increment
	 * @return The new incremented value
	 */
	long increment(CounterType counterType);

	/**
	 * Increments the specified counter by the specified amount,
	 * returning the new value.
	 *
	 * @param counterType The counter to increment
	 * @return The new incremented value
	 */
	long add(CounterType counterType, long amount);

	/**
	 * Sets the specified counter to the specified value,
	 * returning the previous value.
	 *
	 * @param counterType The counter to increment
	 * @return The previous value
	 */
	long set(CounterType counterType, long value);

	/**
	 * Returns the current value of the specified counter.
	 *
	 * @param counterType The counter value to return
	 * @return The current value of the counter
	 */
	long get(CounterType counterType);

	/**
	 * Set a group of values.  Values are updates in such
	 * a way as to prevent read-tearing when {@link #toMap()}
	 * is called.
	 *
	 * @param newValues The values to update.
	 */
	void setAll(Map<CounterType, Long> newValues);

	/**
	 * Returns the current values as a map.
	 * @return the current values as a map
	 */
	Map<String, Object> toMap();
}