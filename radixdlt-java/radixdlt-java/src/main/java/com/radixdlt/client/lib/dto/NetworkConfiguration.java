/* Copyright 2021 Radix DLT Ltd incorporated in England.
 *
 * Licensed under the Radix License, Version 1.0 (the "License"); you may not use this
 * file except in compliance with the License. You may obtain a copy of the License at:
 *
 * radixfoundation.org/licenses/LICENSE-v1
 *
 * The Licensor hereby grants permission for the Canonical version of the Work to be
 * published, distributed and used under or by reference to the Licensor’s trademark
 * Radix ® and use of any unregistered trade names, logos or get-up.
 *
 * The Licensor provides the Work (and each Contributor provides its Contributions) on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied,
 * including, without limitation, any warranties or conditions of TITLE, NON-INFRINGEMENT,
 * MERCHANTABILITY, or FITNESS FOR A PARTICULAR PURPOSE.
 *
 * Whilst the Work is capable of being deployed, used and adopted (instantiated) to create
 * a distributed ledger it is your responsibility to test and validate the code, together
 * with all logic and performance of that code under all foreseeable scenarios.
 *
 * The Licensor does not make or purport to make and hereby excludes liability for all
 * and any representation, warranty or undertaking in any form whatsoever, whether express
 * or implied, to any entity or person, including any representation, warranty or
 * undertaking, as to the functionality security use, value or other characteristics of
 * any distributed ledger nor in respect the functioning or value of any tokens which may
 * be created stored or transferred using the Work. The Licensor does not warrant that the
 * Work or any use of the Work complies with any law or regulation in any territory where
 * it may be implemented or used or that it will be appropriate for any specific purpose.
 *
 * Neither the licensor nor any current or former employees, officers, directors, partners,
 * trustees, representatives, agents, advisors, contractors, or volunteers of the Licensor
 * shall be liable for any direct or indirect, special, incidental, consequential or other
 * losses of any kind, in tort, contract or otherwise (including but not limited to loss
 * of revenue, income or profits, or loss of use or data, or loss of reputation, or loss
 * of any economic or other opportunity of whatsoever nature or howsoever arising), arising
 * out of or in connection with (without limitation of any use, misuse, of any ledger system
 * or use made or its functionality or any performance or operation of any code or protocol
 * caused by bugs or programming or logic errors or otherwise);
 *
 * A. any offer, purchase, holding, use, sale, exchange or transmission of any
 * cryptographic keys, tokens or assets created, exchanged, stored or arising from any
 * interaction with the Work;
 *
 * B. any failure in a transmission or loss of any token or assets keys or other digital
 * artefacts due to errors in transmission;
 *
 * C. bugs, hacks, logic errors or faults in the Work or any communication;
 *
 * D. system software or apparatus including but not limited to losses caused by errors
 * in holding or transmitting tokens by any third-party;
 *
 * E. breaches or failure of security including hacker attacks, loss or disclosure of
 * password, loss of private key, unauthorised use or misuse of such passwords or keys;
 *
 * F. any losses including loss of anticipated savings or other benefits resulting from
 * use of the Work or any changes to the Work (however implemented).
 *
 * You are solely responsible for; testing, validating and evaluation of all operation
 * logic, functionality, security and appropriateness of using the Work for any commercial
 * or non-commercial purpose and for any reproduction or redistribution by You of the
 * Work. You assume all risks associated with Your use of the Work and the exercise of
 * permissions under this License.
 */

package com.radixdlt.client.lib.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.radixdlt.client.lib.api.NodeAddress;

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
	private final NodeAddress nodeAddress;
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
		NodeAddress nodeAddress,
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
		this.nodeAddress = nodeAddress;
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
		@JsonProperty(value = "nodeAddress", required = true) NodeAddress nodeAddress,
		@JsonProperty(value = "seedNodes", required = true) List<String> seedNodes
	) {
		return new NetworkConfiguration(
			defaultPort, maxInboundChannels, broadcastPort, listenAddress,
			channelBufferSize, peerConnectionTimeout, pingTimeout, listenPort,
			discoveryInterval, maxOutboundChannels, peerLivenessCheckInterval,
			nodeAddress, seedNodes
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
			&& nodeAddress.equals(that.nodeAddress)
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
			nodeAddress,
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
			+ ", nodeAddress=" + nodeAddress
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

	public NodeAddress getNodeAddress() {
		return nodeAddress;
	}

	public List<String> getSeedNodes() {
		return seedNodes;
	}
}
