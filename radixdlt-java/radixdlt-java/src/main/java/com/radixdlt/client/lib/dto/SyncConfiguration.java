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

package com.radixdlt.client.lib.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

public final class SyncConfiguration {
	private final long maxLedgerUpdatesRate;
	private final long requestTimeout;
	private final long syncCheckInterval;
	private final long syncCheckMaxPeers;
	private final long ledgerStatusUpdateMaxPeersToNotify;

	private SyncConfiguration(
		long maxLedgerUpdatesRate,
		long requestTimeout,
		long syncCheckInterval,
		long syncCheckMaxPeers,
		long ledgerStatusUpdateMaxPeersToNotify
	) {
		this.maxLedgerUpdatesRate = maxLedgerUpdatesRate;
		this.requestTimeout = requestTimeout;
		this.syncCheckInterval = syncCheckInterval;
		this.syncCheckMaxPeers = syncCheckMaxPeers;
		this.ledgerStatusUpdateMaxPeersToNotify = ledgerStatusUpdateMaxPeersToNotify;
	}

	@JsonCreator
	public static SyncConfiguration create(
		@JsonProperty(value = "maxLedgerUpdatesRate", required = true) long maxLedgerUpdatesRate,
		@JsonProperty(value = "requestTimeout", required = true) long requestTimeout,
		@JsonProperty(value = "syncCheckInterval", required = true) long syncCheckInterval,
		@JsonProperty(value = "syncCheckMaxPeers", required = true) long syncCheckMaxPeers,
		@JsonProperty(value = "ledgerStatusUpdateMaxPeersToNotify", required = true) long ledgerStatusUpdateMaxPeersToNotify
	) {
		return new SyncConfiguration(maxLedgerUpdatesRate, requestTimeout, syncCheckInterval, syncCheckMaxPeers, ledgerStatusUpdateMaxPeersToNotify);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}

		if (!(o instanceof SyncConfiguration)) {
			return false;
		}

		var that = (SyncConfiguration) o;
		return maxLedgerUpdatesRate == that.maxLedgerUpdatesRate
			&& requestTimeout == that.requestTimeout
			&& syncCheckInterval == that.syncCheckInterval
			&& syncCheckMaxPeers == that.syncCheckMaxPeers
			&& ledgerStatusUpdateMaxPeersToNotify == that.ledgerStatusUpdateMaxPeersToNotify;
	}

	@Override
	public int hashCode() {
		return Objects.hash(maxLedgerUpdatesRate, requestTimeout, syncCheckInterval, syncCheckMaxPeers, ledgerStatusUpdateMaxPeersToNotify);
	}

	@Override
	public String toString() {
		return "{maxLedgerUpdatesRate:" + maxLedgerUpdatesRate
			+ ", requestTimeout:" + requestTimeout
			+ ", syncCheckInterval:" + syncCheckInterval
			+ ", syncCheckMaxPeers:" + syncCheckMaxPeers
			+ ", ledgerStatusUpdateMaxPeersToNotify:" + ledgerStatusUpdateMaxPeersToNotify + '}';
	}

	public long getMaxLedgerUpdatesRate() {
		return maxLedgerUpdatesRate;
	}

	public long getRequestTimeout() {
		return requestTimeout;
	}

	public long getSyncCheckInterval() {
		return syncCheckInterval;
	}

	public long getSyncCheckMaxPeers() {
		return syncCheckMaxPeers;
	}

	public long getLedgerStatusUpdateMaxPeersToNotify() {
		return ledgerStatusUpdateMaxPeersToNotify;
	}
}
