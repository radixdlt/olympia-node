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

		MESSAGES_INBOUND_AVG_QUEUED_TIME("messages.inbound.avg_queued_time"),
		MESSAGES_INBOUND_TOTAL_QUEUED_TIME("messages.inbound.total_queued_time"),
		MESSAGES_INBOUND_AVG_PROCESSING_TIME("messages.inbound.avg_processing_time"),
		MESSAGES_INBOUND_TOTAL_PROCESSING_TIME("messages.inbound.total_processing_time"),
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
		NETWORKING_P2P_ACTIVE_INBOUND_CHANNELS("networking.p2p.active_inbound_channels"),
		NETWORKING_P2P_ACTIVE_OUTBOUND_CHANNELS("networking.p2p.active_outbound_channels"),
		NETWORKING_P2P_ACTIVE_CHANNELS("networking.p2p.active_channels"),
		NETWORKING_P2P_CHANNELS_INITIALIZED("networking.p2p.channels_initialized"),

		SIGNATURES_SIGNED("signatures.signed"),
		SIGNATURES_VERIFIED("signatures.verified"),
		TIME_DURATION("time.duration"),

		SERVER_ARCHIVE_TOTAL_RESPONSES("server.archive.total_responses"),
		SERVER_ARCHIVE_OK_RESPONSES("server.archive.ok_responses"),
		SERVER_ARCHIVE_NON_OK_RESPONSES("server.archive.non_ok_responses"),
		SERVER_ARCHIVE_AVG_PROCESSING_TIME("server.archive.avg_processing_time"),
		SERVER_ARCHIVE_TOTAL_PROCESSING_TIME("server.archive.total_processing_time"),
		SERVER_NODE_TOTAL_RESPONSES("server.node.total_responses"),
		SERVER_NODE_OK_RESPONSES("server.node.ok_responses"),
		SERVER_NODE_NON_OK_RESPONSES("server.node.non_ok_responses"),
		SERVER_NODE_AVG_PROCESSING_TIME("server.node.avg_processing_time"),
		SERVER_NODE_TOTAL_PROCESSING_TIME("server.node.total_processing_time");


		private final String jsonPath;

		CounterType(String jsonPath) {
			this.jsonPath = jsonPath;
		}

		public String jsonPath() {
			return jsonPath;
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