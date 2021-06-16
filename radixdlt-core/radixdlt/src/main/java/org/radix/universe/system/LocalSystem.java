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
import com.google.common.collect.ImmutableMap;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.SerializerId2;

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
		int protocolVersion
	) {
		super(key, agent, agentVersion, protocolVersion);
		this.infoSupplier = Objects.requireNonNull(infoSupplier);
	}

	// Property "info" - 1 getter
	@JsonProperty("info")
	@DsonOutput(Output.API)
	public Map<String, Object> getInfo() {
		return this.infoSupplier.getInfo();
	}

	public static LocalSystem create(BFTNode self, InfoSupplier infoSupplier) {
		return new LocalSystem(
			infoSupplier,
			self.getKey(),
			Radix.AGENT,
			Radix.AGENT_VERSION,
			Radix.PROTOCOL_VERSION
		);
	}
}
