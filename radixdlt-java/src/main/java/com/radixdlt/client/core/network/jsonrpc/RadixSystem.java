package com.radixdlt.client.core.network.jsonrpc;


import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.SerializerConstants;
import com.radixdlt.serialization.SerializerDummy;
import com.radixdlt.serialization.SerializerId2;

import com.fasterxml.jackson.annotation.JsonProperty;

@SerializerId2("api.system")
public class RadixSystem {

	// Placeholder for the serializer ID
	@JsonProperty(SerializerConstants.SERIALIZER_NAME)
	@DsonOutput(DsonOutput.Output.ALL)
	private SerializerDummy serializer = SerializerDummy.DUMMY;

	@JsonProperty("version")
	@DsonOutput(DsonOutput.Output.ALL)
	private short version = 100;

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
