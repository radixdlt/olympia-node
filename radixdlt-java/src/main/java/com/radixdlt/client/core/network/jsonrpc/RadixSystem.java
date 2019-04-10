package com.radixdlt.client.core.network.jsonrpc;

import java.util.Map;

import org.radix.serialization2.DsonOutput;
import org.radix.serialization2.DsonOutput.Output;
import org.radix.serialization2.SerializerId2;
import org.radix.serialization2.client.SerializableObject;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.radixdlt.client.core.atoms.Shards;

import static org.radix.serialization2.MapHelper.mapOf;

@SerializerId2("internal.system")
public class RadixSystem extends SerializableObject {
	private Shards shards;

	RadixSystem() {
		// No-arg constructor for serializer
	}

	RadixSystem(long low, long high) {
		this.shards = Shards.range(low, high);
	}

	Shards getShards() {
		return shards;
	}

	@JsonProperty("shards")
	@DsonOutput(Output.ALL)
	Map<String, Object> getJsonShards() {
		return mapOf(
				"low", shards.getLow(),
				"high", shards.getHigh());
	}

	@JsonProperty("shards")
	void setJsonShards(Map<String, Object> props) {
		long low = ((Number) props.get("low")).longValue();
		long high = ((Number) props.get("high")).longValue();
		this.shards = Shards.range(low, high);
	}

	@JsonProperty("agent")
	@DsonOutput(Output.ALL)
	Map<String, Object> getJsonAgent() {
		return mapOf();
	}

	@JsonProperty("agent")
	void setJsonAgent(Map<String, Object> ignored) {
		// Nothing to do here.
	}
}
