package org.radix.atoms.sync.messages;

import org.radix.discovery.DiscoveryCursor;
import org.radix.network.messaging.Message;
import com.radixdlt.serialization.SerializerId2;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.DsonOutput.Output;
import org.radix.shards.ShardSpace;

import com.fasterxml.jackson.annotation.JsonProperty;

@SerializerId2("atom.sync.inventory.request")
public class AtomSyncInventoryRequestMessage extends Message
{
	@JsonProperty("shards")
	@DsonOutput(Output.ALL)
	private ShardSpace 		shards;

	@JsonProperty("cursor")
	@DsonOutput(Output.ALL)
	private DiscoveryCursor cursor;

	AtomSyncInventoryRequestMessage()
	{
		// Serializer only
	}

	public AtomSyncInventoryRequestMessage(ShardSpace shards, DiscoveryCursor cursor)
	{
		super();

		this.shards = shards;
		this.cursor = cursor;
	}

	public ShardSpace getShards()
	{
		return shards;
	}

	public DiscoveryCursor getCursor()
	{
		return cursor;
	}

	@Override
	public String getCommand()
	{
		return "atom.sync.inventory.request";
	}
}
