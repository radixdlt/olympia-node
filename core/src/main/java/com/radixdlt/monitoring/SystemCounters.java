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

package com.radixdlt.monitoring;

import java.util.Map;

/** System counters interface. */
public interface SystemCounters {

  enum CounterType {
    // Please keep these sorted

    BFT_EVENTS_RECEIVED("bft.events_received"),
    BFT_COMMITTED_VERTICES("bft.committed_vertices"),
    /** Number of proposals rejected. */
    BFT_NO_VOTES_SENT("bft.no_votes_sent"),
    /** Number of vote quorums formed. */
    BFT_VOTE_QUORUMS("bft.vote_quorums"),
    /** Number of view-timeout quorums formed. */
    BFT_TIMEOUT_QUORUMS("bft.timeout_quorums"),

    /** Number of times a view-timeout message was broadcast. */
    BFT_PACEMAKER_TIMEOUTS_SENT("bft.pacemaker.timeouts_sent"),
    BFT_PACEMAKER_ROUND("bft.pacemaker.round"),
    BFT_PACEMAKER_PROPOSED_TRANSACTIONS("bft.pacemaker.proposed_transactions"),
    BFT_PACEMAKER_PROPOSALS_SENT("bft.pacemaker.proposals_sent"),
    BFT_PACEMAKER_TIMED_OUT_ROUNDS("bft.pacemaker.timed_out_rounds"),

    BFT_SYNC_REQUESTS_SENT("bft.sync.requests_sent"),
    BFT_SYNC_REQUESTS_RECEIVED("bft.sync.requests_received"),
    BFT_SYNC_REQUEST_TIMEOUTS("bft.sync.request_timeouts"),

    /** Number of views that timed out. Rescheduled timeouts of the same view are not counted */
    BFT_VERTEX_STORE_SIZE("bft.vertex_store.size"),
    BFT_VERTEX_STORE_FORKS("bft.vertex_store.forks"),
    BFT_VERTEX_STORE_REBUILDS("bft.vertex_store.rebuilds"),
    BFT_VERTEX_STORE_INDIRECT_PARENTS("bft.vertex_store.indirect_parents"),

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

    SYNC_INVALID_RESPONSES_RECEIVED("sync.invalid_responses_received"),
    SYNC_VALID_RESPONSES_RECEIVED("sync.valid_responses_received"),
    SYNC_REMOTE_REQUESTS_RECEIVED("sync.remote_requests_received"),
    SYNC_CURRENT_STATE_VERSION("sync.current_state_version"),
    SYNC_TARGET_STATE_VERSION("sync.target_state_version"),

    MEMPOOL_CURRENT_SIZE("mempool.current_size"),
    MEMPOOL_RELAYS_SENT("mempool.relays_sent"),
    MEMPOOL_ADD_SUCCESS("mempool.add_success"),
    MEMPOOL_ADD_FAILURE("mempool.add_failure"),

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

    NETWORKING_TCP_DROPPED_MESSAGES("networking.tcp.dropped_messages"),
    NETWORKING_BYTES_SENT("networking.bytes_sent"),
    NETWORKING_BYTES_RECEIVED("networking.bytes_received"),
    NETWORKING_P2P_ACTIVE_INBOUND_CHANNELS("networking.p2p.active_inbound_channels"),
    NETWORKING_P2P_ACTIVE_OUTBOUND_CHANNELS("networking.p2p.active_outbound_channels"),
    NETWORKING_P2P_ACTIVE_CHANNELS("networking.p2p.active_channels"),
    NETWORKING_P2P_CHANNELS_INITIALIZED("networking.p2p.channels_initialized"),

    SIGNATURES_SIGNED("signatures.signed"),
    SIGNATURES_VERIFIED("signatures.verified"),
    TIME_DURATION("time.duration");

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
   * Increments the specified counter by the specified amount, returning the new value.
   *
   * @param counterType The counter to increment
   * @return The new incremented value
   */
  long add(CounterType counterType, long amount);

  /**
   * Sets the specified counter to the specified value, returning the previous value.
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
   * Set a group of values. Values are updates in such a way as to prevent read-tearing when {@link
   * #toMap()} is called.
   *
   * @param newValues The values to update.
   */
  void setAll(Map<CounterType, Long> newValues);

  /**
   * Returns the current values as a map.
   *
   * @return the current values as a map
   */
  Map<String, Object> toMap();
}
