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

public class NetworkDataNetworking {
	private final NetworkDataNetworkingUdp udp;
	private final NetworkDataNetworkingTcp tcp;
	private final long receivedBytes;
	private final long sentBytes;

	private NetworkDataNetworking(NetworkDataNetworkingUdp udp, NetworkDataNetworkingTcp tcp, long receivedBytes, long sentBytes) {
		this.udp = udp;
		this.tcp = tcp;
		this.receivedBytes = receivedBytes;
		this.sentBytes = sentBytes;
	}

	@JsonCreator
	public static NetworkDataNetworking create(
		@JsonProperty(value = "udp", required = true) NetworkDataNetworkingUdp udp,
		@JsonProperty(value = "tcp", required = true) NetworkDataNetworkingTcp tcp,
		@JsonProperty(value = "receivedBytes", required = true) long receivedBytes,
		@JsonProperty(value = "sentBytes", required = true) long sentBytes
	) {
		return new NetworkDataNetworking(udp, tcp, receivedBytes, sentBytes);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}

		if (!(o instanceof NetworkDataNetworking)) {
			return false;
		}

		var that = (NetworkDataNetworking) o;
		return receivedBytes == that.receivedBytes && sentBytes == that.sentBytes && udp.equals(that.udp) && tcp.equals(that.tcp);
	}

	@Override
	public int hashCode() {
		return Objects.hash(udp, tcp, receivedBytes, sentBytes);
	}

	@Override
	public String toString() {
		return "{" + "udp=" + udp + ", tcp=" + tcp + ", receivedBytes=" + receivedBytes + ", sentBytes=" + sentBytes + '}';
	}

	public NetworkDataNetworkingUdp getUdp() {
		return udp;
	}

	public NetworkDataNetworkingTcp getTcp() {
		return tcp;
	}

	public long getReceivedBytes() {
		return receivedBytes;
	}

	public long getSentBytes() {
		return sentBytes;
	}
}
