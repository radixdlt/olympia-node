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

public class SyncData {
	private final long processed;
	private final long targetStateVersion;
	private final long remoteRequestsProcessed;
	private final long invalidCommandsReceived;
	private final long lastReadMillis;
	private final long targetCurrentDiff;

	private SyncData(
		long processed,
		long targetStateVersion,
		long remoteRequestsProcessed,
		long invalidCommandsReceived,
		long lastReadMillis,
		long targetCurrentDiff
	) {
		this.processed = processed;
		this.targetStateVersion = targetStateVersion;
		this.remoteRequestsProcessed = remoteRequestsProcessed;
		this.invalidCommandsReceived = invalidCommandsReceived;
		this.lastReadMillis = lastReadMillis;
		this.targetCurrentDiff = targetCurrentDiff;
	}

	@JsonCreator
	public static SyncData create(
		@JsonProperty(value = "processed", required = true) long processed,
		@JsonProperty(value = "targetStateVersion", required = true) long targetStateVersion,
		@JsonProperty(value = "remoteRequestsProcessed", required = true) long remoteRequestsProcessed,
		@JsonProperty(value = "invalidCommandsReceived", required = true) long invalidCommandsReceived,
		@JsonProperty(value = "lastReadMillis", required = true) long lastReadMillis,
		@JsonProperty(value = "targetCurrentDiff", required = true) long targetCurrentDiff
	) {
		return new SyncData(
			processed, targetStateVersion, remoteRequestsProcessed,
			invalidCommandsReceived, lastReadMillis, targetCurrentDiff
		);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}

		if (!(o instanceof SyncData)) {
			return false;
		}

		var syncData = (SyncData) o;
		return processed == syncData.processed
			&& targetStateVersion == syncData.targetStateVersion
			&& remoteRequestsProcessed == syncData.remoteRequestsProcessed
			&& invalidCommandsReceived == syncData.invalidCommandsReceived
			&& lastReadMillis == syncData.lastReadMillis
			&& targetCurrentDiff == syncData.targetCurrentDiff;
	}

	@Override
	public int hashCode() {
		return Objects.hash(
			processed, targetStateVersion, remoteRequestsProcessed,
			invalidCommandsReceived, lastReadMillis, targetCurrentDiff
		);
	}

	@Override
	public String toString() {
		return "{processed:" + processed
			+ ", targetStateVersion:" + targetStateVersion
			+ ", remoteRequestsProcessed:" + remoteRequestsProcessed
			+ ", invalidCommandsReceived:" + invalidCommandsReceived
			+ ", lastReadMillis:" + lastReadMillis
			+ ", targetCurrentDiff:" + targetCurrentDiff + '}';
	}

	public long getProcessed() {
		return processed;
	}

	public long getTargetStateVersion() {
		return targetStateVersion;
	}

	public long getRemoteRequestsProcessed() {
		return remoteRequestsProcessed;
	}

	public long getInvalidCommandsReceived() {
		return invalidCommandsReceived;
	}

	public long getLastReadMillis() {
		return lastReadMillis;
	}

	public long getTargetCurrentDiff() {
		return targetCurrentDiff;
	}
}
