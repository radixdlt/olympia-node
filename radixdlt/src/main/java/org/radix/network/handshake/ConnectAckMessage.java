package org.radix.network.handshake;

import org.radix.network.messaging.Message;
import com.radixdlt.serialization.SerializerId2;

@SerializerId2("network.message.handshake_connectack")
public final class ConnectAckMessage extends Message
{
	public ConnectAckMessage()
	{
		super();
	}

	@Override
	public String getCommand()
	{
		return "connectack";
	}
}