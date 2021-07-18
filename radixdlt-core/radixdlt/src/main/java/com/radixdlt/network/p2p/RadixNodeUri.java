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

package com.radixdlt.network.p2p;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.identifiers.NodeAddressing;
import com.radixdlt.networks.Addressing;
import com.radixdlt.serialization.DeserializeException;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

public final class RadixNodeUri {
	private final String host;
	private final int port;
	private final String networkNodeHrp;
	private final NodeId nodeId;

	@JsonCreator
	public static RadixNodeUri deserialize(byte[] uri) throws URISyntaxException, DeserializeException {
		return fromUri(new URI(new String(uri)));
	}

	public static RadixNodeUri fromPubKeyAndAddress(int networkId, ECPublicKey publicKey, String host, int port) {
		var hrp = Addressing.ofNetworkId(networkId).forNodes().getHrp();
		return new RadixNodeUri(host, port, hrp, NodeId.fromPublicKey(publicKey));
	}

	public static RadixNodeUri fromUri(URI uri) throws DeserializeException {
		var hrpAndKey = NodeAddressing.parseUnknownHrp(uri.getUserInfo());
		return new RadixNodeUri(uri.getHost(), uri.getPort(), hrpAndKey.getFirst(), NodeId.fromPublicKey(hrpAndKey.getSecond()));
	}

	private RadixNodeUri(String host, int port, String networkNodeHrp, NodeId nodeId) {
		if (port <= 0) {
			throw new RuntimeException("Port must be a positive integer");
		}
		this.host = Objects.requireNonNull(host);
		this.port = port;
		this.networkNodeHrp = networkNodeHrp;
		this.nodeId = Objects.requireNonNull(nodeId);
	}

	public String getHost() {
		return host;
	}

	public int getPort() {
		return port;
	}

	public NodeId getNodeId() {
		return nodeId;
	}

	@JsonValue
	private byte[] getSerializedValue() {
		return getUriString().getBytes(StandardCharsets.UTF_8);
	}

	private String getUriString() {
		return String.format("radix://%s@%s:%s", NodeAddressing.of(networkNodeHrp, nodeId.getPublicKey()), host, port);
	}

	@Override
	public String toString() {
		return getUriString();
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		final var that = (RadixNodeUri) o;
		return port == that.port
			&& Objects.equals(host, that.host)
			&& Objects.equals(nodeId, that.nodeId);
	}

	@Override
	public int hashCode() {
		return Objects.hash(host, port, nodeId);
	}
}
