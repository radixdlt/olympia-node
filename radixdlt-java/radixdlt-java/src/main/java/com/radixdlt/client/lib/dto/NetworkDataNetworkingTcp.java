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

public final class NetworkDataNetworkingTcp {
	private final long outOpened;
	private final long droppedMessages;
	private final long closed;
	private final long inOpened;

	private NetworkDataNetworkingTcp(long outOpened, long droppedMessages, long closed, long inOpened) {
		this.outOpened = outOpened;
		this.droppedMessages = droppedMessages;
		this.closed = closed;
		this.inOpened = inOpened;
	}

	@JsonCreator
	public static NetworkDataNetworkingTcp create(
		@JsonProperty(value = "outOpened", required = true) long outOpened,
		@JsonProperty(value = "droppedMessages", required = true) long droppedMessages,
		@JsonProperty(value = "closed", required = true) long closed,
		@JsonProperty(value = "inOpened", required = true) long inOpened
	) {
		return new NetworkDataNetworkingTcp(outOpened, droppedMessages, closed, inOpened);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}

		if (!(o instanceof NetworkDataNetworkingTcp)) {
			return false;
		}

		var that = (NetworkDataNetworkingTcp) o;
		return outOpened == that.outOpened
			&& droppedMessages == that.droppedMessages
			&& closed == that.closed
			&& inOpened == that.inOpened;
	}

	@Override
	public int hashCode() {
		return Objects.hash(outOpened, droppedMessages, closed, inOpened);
	}

	@Override
	public String toString() {
		return "{" + "outOpened=" + outOpened + ", droppedMessages=" + droppedMessages + ", closed=" + closed
			+ ", inOpened=" + inOpened + '}';
	}

	public long getOutOpened() {
		return outOpened;
	}

	public long getDroppedMessages() {
		return droppedMessages;
	}

	public long getClosed() {
		return closed;
	}

	public long getInOpened() {
		return inOpened;
	}
}
