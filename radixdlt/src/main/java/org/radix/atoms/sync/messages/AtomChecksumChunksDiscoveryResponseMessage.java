package org.radix.atoms.sync.messages;

import org.radix.network.messaging.Message;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.SerializerId2;
import org.radix.utils.BArray;
import com.radixdlt.serialization.DsonOutput.Output;

import com.fasterxml.jackson.annotation.JsonProperty;

@SerializerId2("atom.sync.checksum.chunks.discovery.response")
public class AtomChecksumChunksDiscoveryResponseMessage extends Message
{
	private BArray 	chunkBits;

	@JsonProperty("from")
	@DsonOutput(Output.ALL)
	private int 	from;

	@JsonProperty("to")
	@DsonOutput(Output.ALL)
	private int		to;

	public AtomChecksumChunksDiscoveryResponseMessage()
	{
		super();
	}

	public AtomChecksumChunksDiscoveryResponseMessage(BArray chunkBits, int from, int to)
	{
		super();

		this.chunkBits = BArray.copyOf(chunkBits);
		this.from = from;
		this.to = to;
	}

	@Override
	public String getCommand()
	{
		return "atom.sync.checksum.chunks.discovery.response";
	}

	public BArray getChunkBits()
	{
		return this.chunkBits;
	}

	public int getFrom()
	{
		return this.from;
	}

	public int getTo()
	{
		return this.to;
	}

	@JsonProperty("chunk_bits")
	@DsonOutput(Output.ALL)
	byte[] getJsonChunkBits() {
		return this.chunkBits.toByteArray();
	}

	@JsonProperty("chunk_bits")
	void setJsonChunkBits(byte[] bytes) {
		this.chunkBits = BArray.valueOf(bytes);
	}
}
