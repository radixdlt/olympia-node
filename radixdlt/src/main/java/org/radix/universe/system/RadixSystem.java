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
import java.util.stream.Stream;

import com.radixdlt.common.EUID;
import org.radix.containers.BasicContainer;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.crypto.CryptoException;
import org.radix.network2.transport.TransportInfo;
import org.radix.shards.ShardSpace;

import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.SerializationException;
import com.radixdlt.serialization.SerializerId2;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableList;

import static com.radixdlt.serialization.MapHelper.mapOf;

@SerializerId2("api.system")
public class RadixSystem extends BasicContainer
{
	@Override
	public short VERSION() { return 100; }

	private int  			agentVersion;
	private int  			protocolVersion;

	@JsonProperty("planck")
	@DsonOutput(Output.ALL)
	private int				planck;

	private String 			agent;

	@JsonProperty("timestamp")
	@DsonOutput(Output.ALL)
	private long			timestamp;

	@JsonProperty("transports")
	@DsonOutput(Output.ALL)
	private ImmutableList<TransportInfo> transports;

	@JsonProperty("shards")
	@DsonOutput(Output.ALL)
	private ShardSpace shards;

	private ECPublicKey		key;

	public RadixSystem()
	{
		super();

		this.agent = "unknown";
		this.agentVersion = 0;
		this.planck = 0;
		this.protocolVersion = 0;
		this.timestamp = 0;
		this.transports = ImmutableList.of();
		this.key = null;

		// FIXME: Needs to reflect actual range when shards implemented
		this.shards = new ShardSpace(0L, ShardSpace.SHARD_RANGE_FULL);
	}

	public RadixSystem(ECPublicKey key, String agent, int agentVersion, int protocolVersion, ImmutableList<TransportInfo> transports)
	{
		this();

		this.key = key;
		this.agent = agent;
		this.agentVersion = agentVersion;
		this.protocolVersion = protocolVersion;
		this.transports = transports;
	}

	public String getAgent()
	{
		return this.agent;
	}

	public int getAgentVersion()
	{
		return this.agentVersion;
	}

	public int getProtocolVersion()
	{
		return this.protocolVersion;
	}

	public int getPlanck()
	{
		return this.planck;
	}

	void setPlanck(int planck)
	{
		this.planck = planck;
	}

	public long getTimestamp()
	{
		return this.timestamp;
	}

	public void setTimestamp(long timestamp)
	{
		this.timestamp = timestamp;
	}

	public Stream<TransportInfo> supportedTransports() {
		return transports.stream();
	}

	public ECPublicKey getKey()
	{
		return key;
	}

	void setKey(ECPublicKey key)
	{
		this.key = key;
	}

	public EUID getNID()
	{
		return this.key == null ? EUID.ZERO : this.key.getUID();
	}

	// Property "agent" - 1 getter, 1 setter
	// FIXME: Should be included in a serializable class
	@JsonProperty("agent")
	@DsonOutput(Output.ALL)
	Map<String, Object> getJsonAgent() {
		return mapOf(
				"name", this.agent,
				"version", this.agentVersion,
				"protocol", this.protocolVersion);
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
	byte[] getJsonKey() {
		return (key == null) ? null : key.getBytes();
	}

	@JsonProperty("key")
	void setJsonKey(byte[] newKey) throws SerializationException {
		try {
			key = new ECPublicKey(newKey);
		} catch (CryptoException cex) {
			throw new SerializationException("Invalid key", cex);
		}
	}

	// Property "nid" - 1 getter
	@JsonProperty("nid")
	@DsonOutput(Output.ALL)
	EUID getJsonNid() {
		return this.key == null ? null : this.key.getUID();
	}
}
