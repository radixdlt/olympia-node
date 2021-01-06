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

import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.middleware2.InfoSupplier;
import java.util.Map;
import java.util.Objects;

import org.radix.Radix;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.radixdlt.network.transport.StaticTransportMetadata;
import com.radixdlt.network.transport.TransportInfo;
import com.radixdlt.network.transport.tcp.TCPConstants;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.SerializerId2;
import com.radixdlt.universe.Universe;

@SerializerId2("api.local_system")
// FIXME reimplement localsystem as an interface, extract persistence to elsewhere
public final class LocalSystem extends RadixSystem {
	private final InfoSupplier infoSupplier;

	LocalSystem() {
		// Serializer only
		this(ImmutableMap::of);
	}

	@VisibleForTesting
	LocalSystem(InfoSupplier infoSupplier) {
		this.infoSupplier = infoSupplier;
	}

	public LocalSystem(
		InfoSupplier infoSupplier,
		ECPublicKey key,
		String agent,
		int agentVersion,
		int protocolVersion,
		ImmutableList<TransportInfo> supportedTransports
	) {
		super(key, agent, agentVersion, protocolVersion, supportedTransports);
		this.infoSupplier = Objects.requireNonNull(infoSupplier);
	}

	// Property "info" - 1 getter
	@JsonProperty("info")
	@DsonOutput(Output.API)
	public Map<String, Object> getInfo() {
		return this.infoSupplier.getInfo();
	}

	// Property "processors" - 1 getter
	// No obvious improvements here
	@JsonProperty("processors")
	@DsonOutput(Output.API)
	int getJsonProcessors() {
		return Runtime.getRuntime().availableProcessors();
	}

	public static LocalSystem create(BFTNode self, InfoSupplier infoSupplier, Universe universe, String host) {
		return new LocalSystem(infoSupplier, self.getKey(), Radix.AGENT, Radix.AGENT_VERSION, Radix.PROTOCOL_VERSION, defaultTransports(universe, host));
	}

	// FIXME: *Really* need a better way of configuring this other than hardcoding here
	// Should also have the option of overriding "port", rather than always using universe port
	private static ImmutableList<TransportInfo> defaultTransports(Universe universe, String host) {
		return ImmutableList.of(
			TransportInfo.of(
				TCPConstants.NAME,
				StaticTransportMetadata.of(
					TCPConstants.METADATA_HOST, host,
					TCPConstants.METADATA_PORT, String.valueOf(universe.getPort())
				)
			)
		);
	}
}