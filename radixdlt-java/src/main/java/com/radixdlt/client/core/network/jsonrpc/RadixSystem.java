package com.radixdlt.client.core.network.jsonrpc;


import org.radix.serialization2.DsonOutput;
import org.radix.serialization2.DsonOutput.Output;
import org.radix.serialization2.SerializerId2;
import org.radix.serialization2.client.SerializableObject;

import com.fasterxml.jackson.annotation.JsonProperty;

@SerializerId2("api.system")
public class RadixSystem extends SerializableObject {
	@JsonProperty("shards")
	@DsonOutput(Output.ALL)
	private ShardSpace shards;

	RadixSystem() {
		// No-arg constructor for serializer
	}

	ShardSpace getShards() {
		return shards;
	}
}
