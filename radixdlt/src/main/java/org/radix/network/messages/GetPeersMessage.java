package org.radix.network.messages;

import org.radix.network.messaging.Message;
import com.radixdlt.serialization.SerializerId2;

@SerializerId2("network.message.get_peers")
public final class GetPeersMessage extends Message
{
	public GetPeersMessage(int magic)
	{
		super(magic);
	}

	@Override
	public String getCommand()
	{
		return "peers.get";
	}
}
