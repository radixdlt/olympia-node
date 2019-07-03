package org.radix.atoms.sync.messages;

import org.radix.network.messaging.Message;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.SerializerId2;
import org.radix.shards.ShardRange;
import org.radix.shards.ShardSpace;

import com.fasterxml.jackson.annotation.JsonProperty;

@SerializerId2("atom.sync.checksum.discovery.request")
public class AtomChecksumDiscoveryRequestMessage extends Message
{
	@JsonProperty("index")
	@DsonOutput(Output.ALL)
	private int index;

	@JsonProperty("range")
	@DsonOutput(Output.ALL)
	private ShardRange range;

	AtomChecksumDiscoveryRequestMessage()
	{
		// Serializer only
	}

	public AtomChecksumDiscoveryRequestMessage(int index, ShardRange range)
	{
		super();

		if (range == null)
			throw new IllegalArgumentException("Argument range is null");

		if (index < 0 || index > ShardSpace.SHARD_CHUNKS)
			throw new IllegalArgumentException("Argument index is invalid");

		this.index = index;
		this.range = new ShardRange(range.getLow(), range.getHigh());
	}

	@Override
	public String getCommand()
	{
		return "atom.sync.checksum.discovery.request";
	}

	public int getIndex()
	{
		return this.index;
	}

	public ShardRange getRange()
	{
		return this.range;
	}
}
