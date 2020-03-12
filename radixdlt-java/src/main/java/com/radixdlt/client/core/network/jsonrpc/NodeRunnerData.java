package com.radixdlt.client.core.network.jsonrpc;

import java.util.Map;

import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.SerializerConstants;
import com.radixdlt.serialization.SerializerDummy;
import com.radixdlt.serialization.SerializerId2;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableMap;

@SerializerId2("network.peer")
public class NodeRunnerData {
	private String ip;

	@JsonProperty("system")
	@DsonOutput(Output.ALL)
	private RadixSystem system;

	// Placeholder for the serializer ID
	@JsonProperty(SerializerConstants.SERIALIZER_NAME)
	@DsonOutput(DsonOutput.Output.ALL)
	private SerializerDummy serializer = SerializerDummy.DUMMY;

	@JsonProperty("version")
	@DsonOutput(DsonOutput.Output.ALL)
	private short version = 100;

	NodeRunnerData() {
		// No-arg constructor for serializer
	}

	protected NodeRunnerData(RadixSystem system) {
		this.ip = null;
		this.system = system;
	}

	public ShardSpace getShards() {
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
