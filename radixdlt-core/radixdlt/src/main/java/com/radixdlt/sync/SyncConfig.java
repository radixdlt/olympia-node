/*
 * (C) Copyright 2021 Radix DLT Ltd
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
 */

package com.radixdlt.sync;

import org.json.JSONObject;

/**
 * Configuration parameters for ledger sync.
 */
public interface SyncConfig {
	static SyncConfig of(long requestTimeout, int syncCheckMaxPeers, long syncCheckInterval) {
		return of(requestTimeout, syncCheckMaxPeers, syncCheckInterval, 10, 50);
	}

	static SyncConfig of(
		long requestTimeout,
		int syncCheckMaxPeers,
		long syncCheckInterval,
		int ledgerStatusUpdateMaxPeersToNotify,
		double maxLedgerUpdatesRate
	) {
		return new SyncConfig() {
			@Override
			public long syncCheckReceiveStatusTimeout() {
				return requestTimeout;
			}

			@Override
			public long syncCheckInterval() {
				return syncCheckInterval;
			}

			@Override
			public int syncCheckMaxPeers() {
				return syncCheckMaxPeers;
			}

			@Override
			public long syncRequestTimeout() {
				return requestTimeout;
			}

			@Override
			public int ledgerStatusUpdateMaxPeersToNotify() {
				return ledgerStatusUpdateMaxPeersToNotify;
			}

			@Override
			public double maxLedgerUpdatesRate() {
				return maxLedgerUpdatesRate;
			}

			@Override
			public JSONObject asJson() {
				return new JSONObject()
					.put("sync_check_interval", syncCheckInterval)
					.put("sync_check_max_peers", syncCheckMaxPeers)
					.put("request_timeout", requestTimeout)
					.put("ledger_status_update_max_peers_to_notify", ledgerStatusUpdateMaxPeersToNotify)
					.put("max_ledger_updates_rate", maxLedgerUpdatesRate);
			}
		};
	}

	/**
	 * A timeout for receiving sync check response messages (StatusResponse).
	 */
	long syncCheckReceiveStatusTimeout();

	/**
	 * An interval for executing periodic sync checks with peers.
	 */
	long syncCheckInterval();

	/**
	 * Maximum number of peers to use for sync check.
	 */
	int syncCheckMaxPeers();

	/**
	 * A timeout for peer sync request.
	 */
	long syncRequestTimeout();

	/**
	 * Maximum number of peers to send the LedgerStatusUpdate message to.
	 */
	int ledgerStatusUpdateMaxPeersToNotify();

	/**
	 * Maximum number of LedgerStatusUpdate messages send by this node per second.
	 */
	double maxLedgerUpdatesRate();

	/**
	 * Represent configuration as JSON
	 */

	JSONObject asJson();
}
