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

import java.util.Map;
import java.util.Objects;

import com.radixdlt.crypto.exception.PublicKeyException;
import com.radixdlt.identifiers.EUID;

import org.bouncycastle.util.encoders.Hex;
import org.radix.containers.BasicContainer;
import com.radixdlt.crypto.ECPublicKey;

import com.radixdlt.serialization.DeserializeException;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.SerializerId2;

import com.fasterxml.jackson.annotation.JsonProperty;

import static com.radixdlt.serialization.MapHelper.mapOf;

@SerializerId2("api.system")
public class RadixSystem extends BasicContainer {
	@Override
	public short version() {
		return 100;
	}

	private int agentVersion;
	private int protocolVersion;

	private String agent;

	private ECPublicKey key;

	public RadixSystem() {
		super();

		this.agent = "unknown";
		this.agentVersion = 0;
		this.protocolVersion = 0;
		this.key = null;
	}

	public RadixSystem(ECPublicKey key, String agent, int agentVersion, int protocolVersion) {
		this();

		this.key = key;
		this.agent = agent;
		this.agentVersion = agentVersion;
		this.protocolVersion = protocolVersion;
	}

	public String getAgent() {
		return this.agent;
	}

	public int getAgentVersion() {
		return this.agentVersion;
	}

	public int getProtocolVersion() {
		return this.protocolVersion;
	}

	public ECPublicKey getKey() {
		return key;
	}

	public EUID getNID() {
		return this.key == null ? EUID.ZERO : this.key.euid();
	}

	// Property "agent" - 1 getter, 1 setter
	// FIXME: Should be included in a serializable class
	@JsonProperty("agent")
	@DsonOutput(Output.ALL)
	Map<String, Object> getJsonAgent() {
		return mapOf(
			"name", this.agent,
			"version", this.agentVersion,
			"protocol", this.protocolVersion
		);
	}

	@JsonProperty("agent")
	void setJsonAgent(Map<String, Object> props) {
		this.agent = (String) props.get("name");
		this.agentVersion = ((Number) props.get("version")).intValue();
		this.protocolVersion = ((Number) props.get("protocol")).intValue();
	}

	// Property "key" - 1 getter, 1 setter
	// FIXME: Should serialize ECKeyPair directly
	@JsonProperty("key")
	@DsonOutput(Output.ALL)
	String getJsonKey() {
		return (key == null) ? null : Hex.toHexString(key.getBytes());
	}

	@JsonProperty("key")
	void setJsonKey(String newKey) throws DeserializeException {
		try {
			key = ECPublicKey.fromBytes(Hex.decode(newKey));
		} catch (PublicKeyException cex) {
			throw new DeserializeException("Invalid key", cex);
		}
	}

	// Property "nid" - 1 getter
	@JsonProperty("nid")
	@DsonOutput(Output.ALL)
	EUID getJsonNid() {
		return this.key == null ? null : this.key.euid();
	}

	@Override
	public String toString() {
		return key == null ? "null" : key.euid().toString();
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		RadixSystem that = (RadixSystem) o;
		return agentVersion == that.agentVersion
			&& protocolVersion == that.protocolVersion
			&& Objects.equals(agent, that.agent)
			&& Objects.equals(key, that.key);
	}

	@Override
	public int hashCode() {
		return Objects.hash(agentVersion, protocolVersion, agent, key);
	}
}
