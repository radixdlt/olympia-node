package org.radix.network.peers;

import java.io.IOException;
import java.net.URI;
import org.radix.logging.Logger;
import org.radix.logging.Logging;
import org.radix.network.Protocol;
import org.radix.network.messaging.Message;
import org.radix.network.messaging.Messaging;
import com.radixdlt.serialization.Polymorphic;
import com.radixdlt.serialization.SerializerId2;
import com.google.common.annotations.VisibleForTesting;

@SerializerId2("network.peer")
public class UDPPeer extends Peer implements Polymorphic
{
	private static final Logger networkLog = Logging.getLogger("network");

	// Used by serializer
	UDPPeer()
	{
		super();
	}

	public UDPPeer(URI host, Peer peer) {
		super(host, peer);

		connect();

		networkLog.debug("Connection opened on "+toString());
	}

	@VisibleForTesting
	public UDPPeer(Void doNotUseThisConstructor, URI host) {
		super(host);
	}

	@Override
	public String toString()
	{
		return "UDP "+super.toString();
	}

	@Override
	public void send(Message message) throws IOException
	{
		Messaging.getInstance().send(message, this);
	}

	private void connect() {
		onConnecting();
		onConnected();
	}

	@Override
	void onConnected() {
		super.onConnected();
		addProtocol(Protocol.UDP);
	}
}
