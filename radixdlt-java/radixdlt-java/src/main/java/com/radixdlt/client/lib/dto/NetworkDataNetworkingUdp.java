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

public final class NetworkDataNetworkingUdp {
	private final long droppedMessages;

	private NetworkDataNetworkingUdp(long droppedMessages) {
		this.droppedMessages = droppedMessages;
	}

	@JsonCreator
	public static NetworkDataNetworkingUdp create(
		@JsonProperty(value = "droppedMessages", required = true) long droppedMessages
	) {
		return new NetworkDataNetworkingUdp(droppedMessages);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}

		if (!(o instanceof NetworkDataNetworkingUdp)) {
			return false;
		}

		var that = (NetworkDataNetworkingUdp) o;
		return droppedMessages == that.droppedMessages;
	}

	@Override
	public int hashCode() {
		return Objects.hash(droppedMessages);
	}

	@Override
	public String toString() {
		return "{" + "droppedMessages=" + droppedMessages + '}';
	}

	public long getDroppedMessages() {
		return droppedMessages;
	}
}
