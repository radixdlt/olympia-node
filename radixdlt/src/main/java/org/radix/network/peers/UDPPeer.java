package org.radix.network.peers;

import java.io.IOException;
import java.net.URI;
import org.radix.logging.Logger;
import org.radix.logging.Logging;
import org.radix.network.Protocol;
import org.radix.network.messaging.Message;
import org.radix.network2.messaging.MessageCentral;

import com.radixdlt.serialization.Polymorphic;
import com.radixdlt.serialization.SerializerId2;
import com.google.common.annotations.VisibleForTesting;

@SerializerId2("network.peer")
public class UDPPeer extends Peer implements Polymorphic
{
	private static final Logger networkLog = Logging.getLogger("network");

	private final MessageCentral messageCentral;

	// Used by serializer
	UDPPeer()
	{
		super();
		this.messageCentral = null;
	}

	public UDPPeer(MessageCentral messageCentral, URI host, Peer peer) {
		super(host, peer);

		this.messageCentral = messageCentral;

		connect();

		networkLog.debug("Connectioned opened on "+toString());
	}

	@VisibleForTesting
	public UDPPeer(URI host, Void doNotUseThisConstructor) {
		super(host);
		this.messageCentral = null;
	}

	@Override
	public String toString()
	{
		return "UDP "+super.toString();
	}

	@Override
	public void send(Message message) throws IOException
	{
		// No result checking, we're just going to assume it happened
		messageCentral.send(new UDPPeerWrapper(this), message);
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
