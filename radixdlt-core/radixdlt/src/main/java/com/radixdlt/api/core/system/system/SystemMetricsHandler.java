/*
 * Copyright 2021 Radix Publishing Ltd incorporated in Jersey (Channel Islands).
 * Licensed under the Radix License, Version 1.0 (the "License"); you may not use this
 * file except in compliance with the License. You may obtain a copy of the License at:
 *
 * radixfoundation.org/licenses/LICENSE-v1
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

package com.radixdlt.api.core.system.system;

import com.google.inject.Inject;
import com.radixdlt.api.util.CountersJsonFormatter;
import com.radixdlt.api.util.GetJsonHandler;
import com.radixdlt.counters.SystemCounters;
import org.json.JSONObject;

import java.util.List;

public class SystemMetricsHandler implements GetJsonHandler {
	private final SystemCounters systemCounters;

	static final List<SystemCounters.CounterType> NETWORKING_COUNTERS = List.of(
		SystemCounters.CounterType.MESSAGES_INBOUND_RECEIVED,
		SystemCounters.CounterType.MESSAGES_INBOUND_PROCESSED,
		SystemCounters.CounterType.MESSAGES_INBOUND_DISCARDED,
		SystemCounters.CounterType.MESSAGES_OUTBOUND_ABORTED,
		SystemCounters.CounterType.MESSAGES_OUTBOUND_PENDING,
		SystemCounters.CounterType.MESSAGES_OUTBOUND_PROCESSED,
		SystemCounters.CounterType.MESSAGES_OUTBOUND_SENT,
		SystemCounters.CounterType.NETWORKING_UDP_DROPPED_MESSAGES,
		SystemCounters.CounterType.NETWORKING_TCP_DROPPED_MESSAGES,
		SystemCounters.CounterType.NETWORKING_TCP_IN_OPENED,
		SystemCounters.CounterType.NETWORKING_TCP_OUT_OPENED,
		SystemCounters.CounterType.NETWORKING_TCP_CLOSED,
		SystemCounters.CounterType.NETWORKING_SENT_BYTES,
		SystemCounters.CounterType.NETWORKING_RECEIVED_BYTES
	);

	static final List<SystemCounters.CounterType> SYNC_COUNTERS = List.of(
		SystemCounters.CounterType.SYNC_LAST_READ_MILLIS,
		SystemCounters.CounterType.SYNC_INVALID_COMMANDS_RECEIVED,
		SystemCounters.CounterType.SYNC_PROCESSED,
		SystemCounters.CounterType.SYNC_TARGET_STATE_VERSION,
		SystemCounters.CounterType.SYNC_TARGET_CURRENT_DIFF,
		SystemCounters.CounterType.SYNC_REMOTE_REQUESTS_PROCESSED
	);

	static final List<SystemCounters.CounterType> BFT_COUNTERS = List.of(
		SystemCounters.CounterType.BFT_CONSENSUS_EVENTS,
		SystemCounters.CounterType.BFT_INDIRECT_PARENT,
		SystemCounters.CounterType.BFT_PROCESSED,
		SystemCounters.CounterType.BFT_PROPOSALS_MADE,
		SystemCounters.CounterType.BFT_REJECTED,
		SystemCounters.CounterType.BFT_TIMEOUT,
		SystemCounters.CounterType.BFT_TIMED_OUT_VIEWS,
		SystemCounters.CounterType.BFT_TIMEOUT_QUORUMS,
		SystemCounters.CounterType.BFT_STATE_VERSION,
		SystemCounters.CounterType.BFT_VERTEX_STORE_SIZE,
		SystemCounters.CounterType.BFT_VERTEX_STORE_FORKS,
		SystemCounters.CounterType.BFT_VERTEX_STORE_REBUILDS,
		SystemCounters.CounterType.BFT_VOTE_QUORUMS,
		SystemCounters.CounterType.BFT_SYNC_REQUESTS_SENT,
		SystemCounters.CounterType.BFT_SYNC_REQUEST_TIMEOUTS
	);

	static final List<SystemCounters.CounterType> MEMPOOL_COUNTERS = List.of(
		SystemCounters.CounterType.MEMPOOL_COUNT,
		SystemCounters.CounterType.MEMPOOL_MAXCOUNT,
		SystemCounters.CounterType.MEMPOOL_RELAYER_SENT_COUNT,
		SystemCounters.CounterType.MEMPOOL_ADD_SUCCESS,
		SystemCounters.CounterType.MEMPOOL_PROPOSED_TRANSACTION,
		SystemCounters.CounterType.MEMPOOL_ERRORS_HOOK,
		SystemCounters.CounterType.MEMPOOL_ERRORS_CONFLICT,
		SystemCounters.CounterType.MEMPOOL_ERRORS_OTHER
	);

	@Inject
	SystemMetricsHandler(SystemCounters systemCounters) {
		this.systemCounters = systemCounters;
	}

	@Override
	public JSONObject handleRequest() {
		return new JSONObject()
			.put("mempool", CountersJsonFormatter.countersToJson(systemCounters, MEMPOOL_COUNTERS, true))
			.put("bft", CountersJsonFormatter.countersToJson(systemCounters, BFT_COUNTERS, true))
			.put("sync", CountersJsonFormatter.countersToJson(systemCounters, SYNC_COUNTERS, true))
			.put("networking", CountersJsonFormatter.countersToJson(systemCounters, NETWORKING_COUNTERS, true));
	}
}
