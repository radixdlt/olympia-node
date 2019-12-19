package org.radix.network.messages;

import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.SerializerId2;

import org.radix.universe.system.RadixSystem;
import org.radix.universe.system.SystemMessage;

import com.fasterxml.jackson.annotation.JsonProperty;

@SerializerId2("peer.ping")
public final class PeerPingMessage extends SystemMessage
{
	@JsonProperty("nonce")
	@DsonOutput(Output.ALL)
	private long nonce;

	private PeerPingMessage() {
		// for serializer
	}

	public PeerPingMessage(long nonce, RadixSystem system, int magic)
	{
		super(system, magic);
		this.nonce = nonce;
	}

	@Override
	public String getCommand()
	{
		return "peer.ping";
	}

	public long getNonce() { return nonce; }
}
