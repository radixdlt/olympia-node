package org.radix.network.messages;

import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.SerializerId2;
import com.radixdlt.serialization.DsonOutput.Output;
import org.radix.universe.system.SystemMessage;

import com.fasterxml.jackson.annotation.JsonProperty;

@SerializerId2("peer.pong")
public final class PeerPongMessage extends SystemMessage
{
	@JsonProperty("nonce")
	@DsonOutput(Output.ALL)
	private long nonce;

	public PeerPongMessage()
	{
		super();

		this.nonce = 0l;
	}

	public PeerPongMessage(long nonce)
	{
		this();

		this.nonce = nonce;
	}

	@Override
	public String getCommand()
	{
		return "peer.pong";
	}

	public long getNonce() { return nonce; }
}
