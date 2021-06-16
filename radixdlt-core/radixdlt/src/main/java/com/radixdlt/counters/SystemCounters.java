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

		BFT_CONSENSUS_EVENTS("bft.consensus_events"),
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

		PACEMAKER_VIEW("pacemaker.view"),

		// Count of database accesses
		COUNT_BDB_LEDGER_COMMIT("count.bdb.ledger.commit"),
		COUNT_BDB_LEDGER_CREATE_TX("count.bdb.ledger.create_tx"),
		COUNT_BDB_LEDGER_CONTAINS("count.bdb.ledger.contains"),
		COUNT_BDB_LEDGER_CONTAINS_TX("count.bdb.ledger.contains_tx"),
		COUNT_BDB_LEDGER_ENTRIES("count.bdb.ledger.entries"),
		COUNT_BDB_LEDGER_GET("count.bdb.ledger.get"),
		COUNT_BDB_LEDGER_GET_FIRST("count.bdb.ledger.get_first"),
		COUNT_BDB_LEDGER_GET_LAST("count.bdb.ledger.get_last"),
		COUNT_BDB_LEDGER_GET_NEXT("count.bdb.ledger.get_next"),
		COUNT_BDB_LEDGER_GET_PREV("count.bdb.ledger.get_prev"),
		COUNT_BDB_LEDGER_STORE("count.bdb.ledger.store"),
		COUNT_BDB_LEDGER_LAST_COMMITTED("count.bdb.ledger.last_committed"),
		COUNT_BDB_LEDGER_LAST_VERTEX("count.bdb.ledger.last_vertex"),
		COUNT_BDB_LEDGER_SAVE("count.bdb.ledger.save"),
		COUNT_BDB_LEDGER_SEARCH("count.bdb.ledger.search"),
		COUNT_BDB_LEDGER_TOTAL("count.bdb.ledger.total"),
		COUNT_BDB_LEDGER_BYTES_READ("count.bdb.ledger.bytes.read"),
		COUNT_BDB_LEDGER_BYTES_WRITE("count.bdb.ledger.bytes.write"),
		COUNT_BDB_LEDGER_DELETES("count.bdb.ledger.deletes"),
		COUNT_BDB_LEDGER_PROOFS_ADDED("count.bdb.ledger.proofs.added"),
		COUNT_BDB_LEDGER_PROOFS_REMOVED("count.bdb.ledger.proofs.removed"),

		COUNT_BDB_ADDRESS_BOOK_TOTAL("count.bdb.address_book.total"),
		COUNT_BDB_ADDRESS_BOOK_BYTES_READ("count.bdb.address_book.bytes.read"),
		COUNT_BDB_ADDRESS_BOOK_BYTES_WRITE("count.bdb.address_book.bytes.write"),
		COUNT_BDB_ADDRESS_BOOK_DELETES("count.bdb.address_book.deletes"),

		COUNT_BDB_SAFETY_STATE_TOTAL("count.bdb.safety_state.total"),
		COUNT_BDB_SAFETY_STATE_BYTES_READ("count.bdb.safety_state.bytes.read"),
		COUNT_BDB_SAFETY_STATE_BYTES_WRITE("count.bdb.safety_state.bytes.write"),

		COUNT_BDB_HEADER_BYTES_WRITE("count.bdb.header.bytes.write"),

		// API DB metrics
		COUNT_APIDB_QUEUE_SIZE("count.apidb.queue.size"),
		COUNT_APIDB_FLUSH_COUNT("count.apidb.flush.count"),

		COUNT_APIDB_BALANCE_TOTAL("count.apidb.balance.total"),
		COUNT_APIDB_BALANCE_READ("count.apidb.balance.read"),
		COUNT_APIDB_BALANCE_WRITE("count.apidb.balance.write"),
		COUNT_APIDB_BALANCE_BYTES_READ("count.apidb.balance.bytes.read"),
		COUNT_APIDB_BALANCE_BYTES_WRITE("count.apidb.balance.bytes.write"),

		COUNT_APIDB_TOKEN_TOTAL("count.apidb.token.total"),
		COUNT_APIDB_TOKEN_READ("count.apidb.token.read"),
		COUNT_APIDB_TOKEN_WRITE("count.apidb.token.write"),
		COUNT_APIDB_TOKEN_BYTES_READ("count.apidb.token.bytes.read"),
		COUNT_APIDB_TOKEN_BYTES_WRITE("count.apidb.token.bytes.write"),

		COUNT_APIDB_TRANSACTION_TOTAL("count.apidb.transaction.total"),
		COUNT_APIDB_TRANSACTION_READ("count.apidb.transaction.read"),
		COUNT_APIDB_TRANSACTION_WRITE("count.apidb.transaction.write"),
		COUNT_APIDB_TRANSACTION_BYTES_READ("count.apidb.transaction.bytes.read"),
		COUNT_APIDB_TRANSACTION_BYTES_WRITE("count.apidb.transaction.bytes.write"),

		// Total elapsed time for database access, in microseconds
		ELAPSED_APIDB_BALANCE_READ("elapsed.apidb.balance.read"),
		ELAPSED_APIDB_BALANCE_WRITE("elapsed.apidb.balance.write"),

		ELAPSED_APIDB_TOKEN_READ("elapsed.apidb.token.read"),
		ELAPSED_APIDB_TOKEN_WRITE("elapsed.apidb.token.write"),

		ELAPSED_APIDB_TRANSACTION_READ("elapsed.apidb.transaction.read"),
		ELAPSED_APIDB_TRANSACTION_WRITE("elapsed.apidb.transaction.write"),

		ELAPSED_APIDB_FLUSH_TIME("elapsed.apidb.flush.time"),

		ELAPSED_BDB_ADDRESS_BOOK("elapsed.bdb.address_book"),

		ELAPSED_BDB_LEDGER_COMMIT("elapsed.bdb.ledger.commit"),
		ELAPSED_BDB_LEDGER_CREATE_TX("elapsed.bdb.ledger.create_tx"),
		ELAPSED_BDB_LEDGER_CONTAINS("elapsed.bdb.ledger.contains"),
		ELAPSED_BDB_LEDGER_CONTAINS_TX("elapsed.bdb.ledger.contains_tx"),
		ELAPSED_BDB_LEDGER_ENTRIES("elapsed.bdb.ledger.entries"),
		ELAPSED_BDB_LEDGER_GET("elapsed.bdb.ledger.get"),
		ELAPSED_BDB_LEDGER_GET_FIRST("elapsed.bdb.ledger.get_first"),
		ELAPSED_BDB_LEDGER_GET_LAST("elapsed.bdb.ledger.get_last"),
		ELAPSED_BDB_LEDGER_STORE("elapsed.bdb.ledger.store"),
		ELAPSED_BDB_LEDGER_LAST_COMMITTED("elapsed.bdb.ledger.last_committed"),
		ELAPSED_BDB_LEDGER_LAST_VERTEX("elapsed.bdb.ledger.last_vertex"),
		ELAPSED_BDB_LEDGER_SAVE("elapsed.bdb.ledger.save"),
		ELAPSED_BDB_LEDGER_SEARCH("elapsed.bdb.ledger.search"),
		ELAPSED_BDB_LEDGER_TOTAL("elapsed.bdb.ledger.total"),

		ELAPSED_BDB_SAFETY_STATE("elapsed.bdb.safety_state"),

		PERSISTENCE_VERTEX_STORE_SAVES("persistence.vertex_store_saves"),
		PERSISTENCE_SAFETY_STORE_SAVES("persistence.safety_store_saves"),

		PERSISTENCE_ATOM_LOG_WRITE_BYTES("persistence.atom_log.write_bytes"),
		PERSISTENCE_ATOM_LOG_WRITE_COMPRESSED("persistence.atom_log.write_compressed"),

		EPOCH_MANAGER_QUEUED_CONSENSUS_EVENTS("epoch_manager.queued_consensus_events"),

		STARTUP_TIME_MS("startup.time_ms"),

		HASHED_BYTES("hashed.bytes"),

		LEDGER_STATE_VERSION("ledger.state_version"),
		LEDGER_SYNC_COMMANDS_PROCESSED("ledger.sync_commands_processed"),
		LEDGER_BFT_COMMANDS_PROCESSED("ledger.bft_commands_processed"),

		SYNC_LAST_READ_MILLIS("sync.last_read_millis"),
		SYNC_INVALID_COMMANDS_RECEIVED("sync.invalid_commands_received"),
		SYNC_PROCESSED("sync.processed"),
		SYNC_TARGET_STATE_VERSION("sync.target_state_version"),
		SYNC_TARGET_CURRENT_DIFF("sync.target_current_diff"),
		SYNC_REMOTE_REQUESTS_PROCESSED("sync.remote_requests_processed"),

		MEMPOOL_COUNT("mempool.count"),
		MEMPOOL_MAXCOUNT("mempool.maxcount"),
		MEMPOOL_RELAYER_SENT_COUNT("mempool.relayer_sent_count"),
		MEMPOOL_ADD_SUCCESS("mempool.add_success"),
		MEMPOOL_PROPOSED_TRANSACTION("mempool.proposed_transaction"),
		MEMPOOL_ERRORS_HOOK("mempool.errors.hook"),
		MEMPOOL_ERRORS_CONFLICT("mempool.errors.conflict"),
		MEMPOOL_ERRORS_OTHER("mempool.errors.other"),

		RADIX_ENGINE_INVALID_PROPOSED_COMMANDS("radix_engine.invalid_proposed_commands"),
		RADIX_ENGINE_USER_TRANSACTIONS("radix_engine.user_transactions"),
		RADIX_ENGINE_SYSTEM_TRANSACTIONS("radix_engine.system_transactions"),

		MESSAGES_INBOUND_RECEIVED("messages.inbound.received"),
		MESSAGES_INBOUND_PROCESSED("messages.inbound.processed"),
		MESSAGES_INBOUND_DISCARDED("messages.inbound.discarded"),
		MESSAGES_OUTBOUND_ABORTED("messages.outbound.aborted"),
		MESSAGES_OUTBOUND_PENDING("messages.outbound.pending"),
		MESSAGES_OUTBOUND_PROCESSED("messages.outbound.processed"),
		MESSAGES_OUTBOUND_SENT("messages.outbound.sent"),

		NETWORKING_UDP_DROPPED_MESSAGES("networking.udp.dropped_messages"),
		NETWORKING_TCP_DROPPED_MESSAGES("networking.tcp.dropped_messages"),
		NETWORKING_TCP_IN_OPENED("networking.tcp.in_opened"),
		NETWORKING_TCP_OUT_OPENED("networking.tcp.out_opened"),
		NETWORKING_TCP_CLOSED("networking.tcp.closed"),
		NETWORKING_SENT_BYTES("networking.sent_bytes"),
		NETWORKING_RECEIVED_BYTES("networking.received_bytes"),

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