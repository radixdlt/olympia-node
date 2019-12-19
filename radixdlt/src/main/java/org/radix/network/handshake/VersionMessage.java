package org.radix.network.handshake;

import com.radixdlt.serialization.SerializerId2;
import org.radix.network.messaging.Message;

@SerializerId2("network.message.handshake_version")
public final class VersionMessage extends Message
{
	private VersionMessage() {
		super(0);
		// for serializer
	}

	public VersionMessage(int magic) {
		super(magic);
	}
	
	@Override
	public String getCommand()
	{
		return "version";
	}
}
