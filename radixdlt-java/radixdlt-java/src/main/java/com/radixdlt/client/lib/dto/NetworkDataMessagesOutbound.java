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

public class NetworkDataMessagesOutbound {
		private final long processed;
		private final long aborted;
		private final long pending;
		private final long sent;

	private NetworkDataMessagesOutbound(long processed, long aborted, long pending, long sent) {
		this.processed = processed;
		this.aborted = aborted;
		this.pending = pending;
		this.sent = sent;
	}

	@JsonCreator
	public static NetworkDataMessagesOutbound create(
		@JsonProperty(value = "processed", required = true) long processed,
		@JsonProperty(value = "aborted", required = true) long aborted,
		@JsonProperty(value = "pending", required = true) long pending,
		@JsonProperty(value = "sent", required = true) long sent
	) {
		return new NetworkDataMessagesOutbound(processed, aborted, pending, sent);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}

		if (!(o instanceof NetworkDataMessagesOutbound)) {
			return false;
		}

		var that = (NetworkDataMessagesOutbound) o;
		return processed == that.processed && aborted == that.aborted && pending == that.pending && sent == that.sent;
	}

	@Override
	public int hashCode() {
		return Objects.hash(processed, aborted, pending, sent);
	}

	@Override
	public String toString() {
		return "{" + "processed=" + processed + ", aborted=" + aborted + ", pending=" + pending + ", sent=" + sent + '}';
	}

	public long getProcessed() {
		return processed;
	}

	public long getAborted() {
		return aborted;
	}

	public long getPending() {
		return pending;
	}

	public long getSent() {
		return sent;
	}
}
