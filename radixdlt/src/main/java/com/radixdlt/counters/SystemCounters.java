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
		CONSENSUS_SYNC_SUCCESS,
		CONSENSUS_SYNC_EXCEPTION,
		CONSENSUS_REJECTED,
		CONSENSUS_TIMEOUT,
		CONSENSUS_VIEW,
		LEDGER_PROCESSED,
		LEDGER_STORED,
		MEMPOOL_COUNT,
		MEMPOOL_MAXCOUNT,
		MESSAGES_INBOUND_BADSIGNATURE,
		MESSAGES_INBOUND_DISCARDED,
		MESSAGES_INBOUND_PENDING,
		MESSAGES_INBOUND_PROCESSED,
		MESSAGES_INBOUND_RECEIVED,
		MESSAGES_OUTBOUND_ABORTED,
		MESSAGES_OUTBOUND_PENDING,
		MESSAGES_OUTBOUND_PROCESSED,
		MESSAGES_OUTBOUND_SENT,
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
	 * Returns the current values as a map.
	 * @return the current values as a map
	 */
	Map<String, Object> toMap();
}