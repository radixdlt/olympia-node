package org.radix.atoms.sync.messages;

import org.radix.network.messaging.Message;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.SerializerId2;

import com.fasterxml.jackson.annotation.JsonProperty;

import com.radixdlt.serialization.DsonOutput.Output;

@SerializerId2("atom.sync.checksum.chunks.discovery.request")
public class AtomChecksumChunksDiscoveryRequestMessage extends Message
{
	@JsonProperty("chunk")
	@DsonOutput(Output.ALL)
	private int		chunk;

	@JsonProperty("chunk_mask")
	@DsonOutput(Output.ALL)
	private byte 	chunkMask;

	public AtomChecksumChunksDiscoveryRequestMessage()
	{
		super();
	}

	public AtomChecksumChunksDiscoveryRequestMessage(int chunk, byte chunkMask)
	{
		super();

		this.chunk = chunk;
		this.chunkMask = chunkMask;
	}

	@Override
	public String getCommand()
	{
		return "atom.sync.checksum.chunks.discovery.request";
	}

	public int getChunk()
	{
		return this.chunk;
	}

	public byte getChunkMask()
	{
		return this.chunkMask;
	}
}
