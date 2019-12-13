package org.radix.network.messages;

import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.SerializerId2;

import org.radix.universe.system.SystemMessage;

import com.fasterxml.jackson.annotation.JsonProperty;

@SerializerId2("peer.ping")
public final class PeerPingMessage extends SystemMessage
{
	@JsonProperty("nonce")
	@DsonOutput(Output.ALL)
	private long nonce;

	private PeerPingMessage() {
		this(0L);
	}

	public PeerPingMessage(long nonce)
	{
		super();
		this.nonce = nonce;
	}

	@Override
	public String getCommand()
	{
		return "peer.ping";
	}

	public long getNonce() { return nonce; }
}
