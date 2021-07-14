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
import com.radixdlt.client.lib.api.NodeAddress;

import java.util.List;
import java.util.Objects;

public final class NetworkPeer {
	private final NodeAddress address;
	private final List<NetworkChannel> channels;

	private NetworkPeer(NodeAddress address, List<NetworkChannel> channels) {
		this.address = address;
		this.channels = channels;
	}

	@JsonCreator
	public static NetworkPeer create(
		@JsonProperty(value = "address", required = true) NodeAddress address,
		@JsonProperty(value = "channels", required = true) List<NetworkChannel> channels
	) {
		return new NetworkPeer(address, channels);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}

		if (!(o instanceof NetworkPeer)) {
			return false;
		}

		var that = (NetworkPeer) o;
		return address.equals(that.address) && channels.equals(that.channels);
	}

	@Override
	public int hashCode() {
		return Objects.hash(address, channels);
	}

	@Override
	public String toString() {
		return "{"
			+ "address=" + address
			+ ", channels=" + channels
			+ '}';
	}

	public NodeAddress getAddress() {
		return address;
	}

	public List<NetworkChannel> getChannels() {
		return channels;
	}
}
