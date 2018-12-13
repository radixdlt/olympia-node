package com.radixdlt.client.core.network;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableMap;
import com.radixdlt.client.core.atoms.Shards;
import org.radix.serialization2.DsonOutput;
import org.radix.serialization2.DsonOutput.Output;
import org.radix.serialization2.client.SerializableObject;

import java.util.Map;

public abstract class NodeRunnerData extends SerializableObject {
	private String ip;

	@JsonProperty("system")
	@DsonOutput(Output.ALL)
	private RadixSystem system;

	NodeRunnerData() {
		// No-arg constructor for serializer
	}

	protected NodeRunnerData(String ip, long lowShard, long highShard) {
		this.ip = ip;
		this.system = new RadixSystem(lowShard, highShard);
	}

	protected NodeRunnerData(RadixSystem system) {
		this.ip = null;
		this.system = system;
	}

	public Shards getShards() {
		return system.getShards();
	}

	public String getIp() {
		return ip;
	}

	@Override
	public String toString() {
		return (ip != null ? (ip + ": ") : "") + "shards=" + system.getShards().toString();
	}

	@Override
	public int hashCode() {
		// TODO: fix hack
		return (ip + system.getShards().toString()).hashCode();
	}

	@Override
	public boolean equals(Object o) {
		if (o == null || !(o instanceof NodeRunnerData)) {
			return false;
		}

		NodeRunnerData other = (NodeRunnerData) o;
		return other.ip.equals(ip) && other.system.getShards().equals(this.system.getShards());
	}

	// Property "host" - 1 getter, 1 setter
	// Could potentially just serialize the URI as a string
	@JsonProperty("host")
	@DsonOutput(Output.ALL)
	private Map<String, Object> getJsonHost() {
		return ImmutableMap.of("ip", this.ip);
	}

	@JsonProperty("host")
	private void setJsonHost(Map<String, Object> props) {
		this.ip = props.get("ip").toString();
	}
}
