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

public final class NetworkData {
	private final NetworkDataMessages messages;
	private final NetworkDataNetworking networking;

	private NetworkData(NetworkDataMessages messages, NetworkDataNetworking networking) {
		this.messages = messages;
		this.networking = networking;
	}

	@JsonCreator
	public static NetworkData create(
		@JsonProperty(value = "messages", required = true) NetworkDataMessages messages,
		@JsonProperty(value = "networking", required = true) NetworkDataNetworking networking
	) {
		return new NetworkData(messages, networking);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}

		if (!(o instanceof NetworkData)) {
			return false;
		}

		var that = (NetworkData) o;
		return messages.equals(that.messages) && networking.equals(that.networking);
	}

	@Override
	public int hashCode() {
		return Objects.hash(messages, networking);
	}

	@Override
	public String toString() {
		return "{" + "messages=" + messages + ", networking=" + networking + '}';
	}

	public NetworkDataMessages getMessages() {
		return messages;
	}

	public NetworkDataNetworking getNetworking() {
		return networking;
	}
}
