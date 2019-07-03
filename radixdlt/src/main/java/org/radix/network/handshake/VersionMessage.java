package org.radix.network.handshake;

import org.radix.network.messaging.Message;
import com.radixdlt.serialization.SerializerId2;

@SerializerId2("network.message.handshake_version")
public final class VersionMessage extends Message
{
	public VersionMessage()
	{ 
		super(); 
	}
	
	@Override
	public String getCommand()
	{
		return "version";
	}
}
