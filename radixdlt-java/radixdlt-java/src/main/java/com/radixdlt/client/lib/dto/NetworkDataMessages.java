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

public final class NetworkDataMessages {
	private final NetworkDataMessagesInbound inbound;
	private final NetworkDataMessagesOutbound outbound;

	private NetworkDataMessages(NetworkDataMessagesInbound inbound, NetworkDataMessagesOutbound outbound) {
		this.inbound = inbound;
		this.outbound = outbound;
	}

	@JsonCreator
	public static NetworkDataMessages create(
		@JsonProperty(value = "inbound", required = true) NetworkDataMessagesInbound inbound,
		@JsonProperty(value = "outbound", required = true) NetworkDataMessagesOutbound outbound
	) {
		return new NetworkDataMessages(inbound, outbound);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}

		if (!(o instanceof NetworkDataMessages)) {
			return false;
		}

		var that = (NetworkDataMessages) o;
		return inbound.equals(that.inbound) && outbound.equals(that.outbound);
	}

	@Override
	public int hashCode() {
		return Objects.hash(inbound, outbound);
	}

	@Override
	public String toString() {
		return "{" + "inbound=" + inbound + ", outbound=" + outbound + '}';
	}

	public NetworkDataMessagesInbound getInbound() {
		return inbound;
	}

	public NetworkDataMessagesOutbound getOutbound() {
		return outbound;
	}
}
