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

public class NetworkDataMessagesInbound {
	private final long processed;
	private final long discarded;
	private final long received;

	private NetworkDataMessagesInbound(long processed, long discarded, long received) {
		this.processed = processed;
		this.discarded = discarded;
		this.received = received;
	}

	@JsonCreator
	public static NetworkDataMessagesInbound create(
		@JsonProperty(value = "processed", required = true) long processed,
		@JsonProperty(value = "discarded", required = true) long discarded,
		@JsonProperty(value = "received", required = true) long received
	) {
		return new NetworkDataMessagesInbound(processed, discarded, received);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}

		if (!(o instanceof NetworkDataMessagesInbound)) {
			return false;
		}

		var that = (NetworkDataMessagesInbound) o;
		return processed == that.processed && discarded == that.discarded && received == that.received;
	}

	@Override
	public int hashCode() {
		return Objects.hash(processed, discarded, received);
	}

	@Override
	public String toString() {
		return "{" + "processed=" + processed + ", discarded=" + discarded + ", received=" + received + '}';
	}

	public long getProcessed() {
		return processed;
	}

	public long getDiscarded() {
		return discarded;
	}

	public long getReceived() {
		return received;
	}
}
