package org.radix.network.handshake;

import org.radix.network.messaging.Message;
import com.radixdlt.serialization.SerializerId2;

@SerializerId2("network.message.handshake_verack")
public final class VerackMessage extends Message
{
	public VerackMessage()
	{
		super();
	}

	@Override
	public String getCommand()
	{
		return "verack";
	}
}
