/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 */

package com.radixdlt.test;

import com.google.common.collect.ImmutableMap;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Map;

public final class SystemCounters {
	private final Map<SystemCounterType, Long> systemCounters;

	private SystemCounters(Map<SystemCounterType, Long> systemCounters) {
		this.systemCounters = systemCounters;
	}

	public long get(SystemCounterType counterType) {
		return systemCounters.get(counterType);
	}

	public static SystemCounters from(JSONObject jsonCounters) {
		ImmutableMap.Builder<SystemCounterType, Long> systemCounters = ImmutableMap.builder();
		for (SystemCounterType value : SystemCounterType.values()) {
			try {
				String[] path = value.toString().toLowerCase().split("_");
				JSONObject parent = jsonCounters;
				for (int i = 0; i < path.length - 1; i++) {
					parent = parent.getJSONObject(path[i]);
				}
				long counterValue = parent.getLong(path[path.length - 1]);
				systemCounters.put(value, counterValue);
			} catch (JSONException e) {
				System.err.println("failed to extract value for " + value + ": " + e);
				systemCounters.put(value, 0L);
			}
		}
		return new SystemCounters(systemCounters.build());
	}

	public enum SystemCounterType {
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
}
