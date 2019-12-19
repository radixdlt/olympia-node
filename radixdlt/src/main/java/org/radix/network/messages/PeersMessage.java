package org.radix.network.messages;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.radix.network.messaging.Message;
import org.radix.network2.addressbook.Peer;

import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.SerializerId2;

import com.fasterxml.jackson.annotation.JsonProperty;

@SerializerId2("network.message.peers")
public final class PeersMessage extends Message
{
	@JsonProperty("peers")
	@DsonOutput(Output.ALL)
	private List<Peer> peers = new ArrayList<>();

	private PeersMessage() {
		super(0);
		// For serializer
	}

	public PeersMessage(int magic)
	{
		super(magic);
	}

	@Override
	public String getCommand()
	{
		return "peers";
	}

	public List<Peer> getPeers() { return peers; }

	public void setPeers(Collection<Peer> peers)
	{
		this.peers.clear();
		this.peers.addAll(peers);
	}
}
