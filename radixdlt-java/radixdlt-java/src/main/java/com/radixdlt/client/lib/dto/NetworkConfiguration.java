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

import java.util.List;
import java.util.Objects;

public final class NetworkConfiguration {
	private final long defaultPort;
	private final long maxInboundChannels;
	private final long broadcastPort;
	private final String listenAddress;
	private final long channelBufferSize;
	private final long peerConnectionTimeout;
	private final long pingTimeout;
	private final long listenPort;
	private final long discoveryInterval;
	private final long maxOutboundChannels;
	private final long peerLivenessCheckInterval;
	private final List<String> seedNodes;

	private NetworkConfiguration(
		long defaultPort,
		long maxInboundChannels,
		long broadcastPort,
		String listenAddress,
		long channelBufferSize,
		long peerConnectionTimeout,
		long pingTimeout,
		long listenPort,
		long discoveryInterval,
		long maxOutboundChannels,
		long peerLivenessCheckInterval,
		List<String> seedNodes
	) {
		this.defaultPort = defaultPort;
		this.maxInboundChannels = maxInboundChannels;
		this.broadcastPort = broadcastPort;
		this.listenAddress = listenAddress;
		this.channelBufferSize = channelBufferSize;
		this.peerConnectionTimeout = peerConnectionTimeout;
		this.pingTimeout = pingTimeout;
		this.listenPort = listenPort;
		this.discoveryInterval = discoveryInterval;
		this.maxOutboundChannels = maxOutboundChannels;
		this.peerLivenessCheckInterval = peerLivenessCheckInterval;
		this.seedNodes = seedNodes;
	}

	@JsonCreator
	public static NetworkConfiguration create(
		@JsonProperty(value = "defaultPort", required = true) long defaultPort,
		@JsonProperty(value = "maxInboundChannels", required = true) long maxInboundChannels,
		@JsonProperty(value = "broadcastPort", required = true) long broadcastPort,
		@JsonProperty(value = "listenAddress", required = true) String listenAddress,
		@JsonProperty(value = "channelBufferSize", required = true) long channelBufferSize,
		@JsonProperty(value = "peerConnectionTimeout", required = true) long peerConnectionTimeout,
		@JsonProperty(value = "pingTimeout", required = true) long pingTimeout,
		@JsonProperty(value = "listenPort", required = true) long listenPort,
		@JsonProperty(value = "discoveryInterval", required = true) long discoveryInterval,
		@JsonProperty(value = "maxOutboundChannels", required = true) long maxOutboundChannels,
		@JsonProperty(value = "peerLivenessCheckInterval", required = true) long peerLivenessCheckInterval,
		@JsonProperty(value = "seedNodes", required = true) List<String> seedNodes
	) {
		return new NetworkConfiguration(
			defaultPort, maxInboundChannels, broadcastPort, listenAddress, channelBufferSize, peerConnectionTimeout,
			pingTimeout, listenPort, discoveryInterval, maxOutboundChannels, peerLivenessCheckInterval, seedNodes
		);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}

		if (!(o instanceof NetworkConfiguration)) {
			return false;
		}

		var that = (NetworkConfiguration) o;
		return defaultPort == that.defaultPort
			&& maxInboundChannels == that.maxInboundChannels
			&& broadcastPort == that.broadcastPort
			&& channelBufferSize == that.channelBufferSize
			&& peerConnectionTimeout == that.peerConnectionTimeout
			&& pingTimeout == that.pingTimeout
			&& listenPort == that.listenPort
			&& discoveryInterval == that.discoveryInterval
			&& maxOutboundChannels == that.maxOutboundChannels
			&& peerLivenessCheckInterval == that.peerLivenessCheckInterval
			&& listenAddress.equals(that.listenAddress)
			&& seedNodes.equals(that.seedNodes);
	}

	@Override
	public int hashCode() {
		return Objects.hash(
			defaultPort,
			maxInboundChannels,
			broadcastPort,
			listenAddress,
			channelBufferSize,
			peerConnectionTimeout,
			pingTimeout,
			listenPort,
			discoveryInterval,
			maxOutboundChannels,
			peerLivenessCheckInterval,
			seedNodes
		);
	}

	@Override
	public String toString() {
		return "{"
			+ "defaultPort=" + defaultPort
			+ ", maxInboundChannels=" + maxInboundChannels
			+ ", broadcastPort=" + broadcastPort
			+ ", listenAddress='" + listenAddress + '\''
			+ ", channelBufferSize=" + channelBufferSize
			+ ", peerConnectionTimeout=" + peerConnectionTimeout
			+ ", pingTimeout=" + pingTimeout
			+ ", listenPort=" + listenPort
			+ ", discoveryInterval=" + discoveryInterval
			+ ", maxOutboundChannels=" + maxOutboundChannels
			+ ", peerLivenessCheckInterval=" + peerLivenessCheckInterval
			+ ", seedNodes=" + seedNodes
			+ '}';
	}


	public long getDefaultPort() {
		return defaultPort;
	}

	public long getMaxInboundChannels() {
		return maxInboundChannels;
	}

	public long getBroadcastPort() {
		return broadcastPort;
	}

	public String getListenAddress() {
		return listenAddress;
	}

	public long getChannelBufferSize() {
		return channelBufferSize;
	}

	public long getPeerConnectionTimeout() {
		return peerConnectionTimeout;
	}

	public long getPingTimeout() {
		return pingTimeout;
	}

	public long getListenPort() {
		return listenPort;
	}

	public long getDiscoveryInterval() {
		return discoveryInterval;
	}

	public long getMaxOutboundChannels() {
		return maxOutboundChannels;
	}

	public long getPeerLivenessCheckInterval() {
		return peerLivenessCheckInterval;
	}

	public List<String> getSeedNodes() {
		return seedNodes;
	}
}
