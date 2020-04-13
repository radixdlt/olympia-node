/*
 * (C) Copyright 2020 Radix DLT Ltd
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

package org.radix.universe.system;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

import org.radix.Radix;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.crypto.CryptoException;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.keys.Keys;
import com.radixdlt.network.transport.DynamicTransportMetadata;
import com.radixdlt.network.transport.TransportInfo;
import com.radixdlt.network.transport.tcp.TCPConstants;
import com.radixdlt.network.transport.udp.PublicInetAddress;
import com.radixdlt.properties.RuntimeProperties;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.SerializerId2;
import com.radixdlt.universe.Universe;

@SerializerId2("api.local_system")
// FIXME reimplement localsystem as an interface, extract persistence to elsewhere
public final class LocalSystem extends RadixSystem {
	private final ECKeyPair keyPair;
	private final Supplier<Map<String, Object>> counters;

	LocalSystem() {
		// Serializer only
		this(ImmutableMap::of);
	}

	@VisibleForTesting
	LocalSystem(Supplier<Map<String, Object>> counters) {
		this.keyPair = ECKeyPair.generateNew();
		this.counters = counters;
	}

	public LocalSystem(Supplier<Map<String, Object>> counters, ECKeyPair key, String agent, int agentVersion, int protocolVersion, ImmutableList<TransportInfo> supportedTransports) {
		super(key.getPublicKey(), agent, agentVersion, protocolVersion, supportedTransports);
		this.keyPair = key;
		this.counters = Objects.requireNonNull(counters);
	}

	public ECKeyPair getKeyPair() {
		return this.keyPair;
	}

	// Property "counters" - 1 getter
	@JsonProperty("counters")
	@DsonOutput(Output.API)
	Map<String, Object> getJsonCounters() {
		return this.counters.get();
	}

	// Property "processors" - 1 getter
	// No obvious improvements here
	@JsonProperty("processors")
	@DsonOutput(Output.API)
	int getJsonProcessors() {
		return Runtime.getRuntime().availableProcessors();
	}

	public static LocalSystem create(SystemCounters counters, RuntimeProperties properties, Universe universe) {
		String nodeKeyPath = properties.get("node.key.path", "node.ks");
		ECKeyPair nodeKey = loadNodeKey(nodeKeyPath);
		return new LocalSystem(counters::toMap, nodeKey, Radix.AGENT, Radix.AGENT_VERSION, Radix.PROTOCOL_VERSION, defaultTransports(universe));
	}

	// FIXME: *Really* need a better way of configuring this other than hardcoding here
	// Should also have the option of overriding "port", rather than always using universe port
	private static ImmutableList<TransportInfo> defaultTransports(Universe universe) {
		return ImmutableList.of(
			TransportInfo.of(
				TCPConstants.NAME,
				DynamicTransportMetadata.of(
					TCPConstants.METADATA_HOST, PublicInetAddress.getInstance()::toString,
					TCPConstants.METADATA_PORT, () -> Integer.toString(universe.getPort())
				)
			)
		);
	}

	private static ECKeyPair loadNodeKey(String nodeKeyPath) {
		try {
			return Keys.readKey(nodeKeyPath, "node", "RADIX_NODE_KEYSTORE_PASSWORD", "RADIX_NODE_KEY_PASSWORD");
		} catch (IOException | CryptoException ex) {
			throw new IllegalStateException("while loading node key", ex);
		}
	}
}