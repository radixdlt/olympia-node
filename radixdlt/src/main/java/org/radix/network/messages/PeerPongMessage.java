package org.radix.network.messages;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.SerializerId2;
import org.radix.universe.system.RadixSystem;
import org.radix.universe.system.SystemMessage;

@SerializerId2("peer.pong")
public final class PeerPongMessage extends SystemMessage {
	@JsonProperty("nonce")
	@DsonOutput(Output.ALL)
	private long nonce;

	private PeerPongMessage() {
		// for serializer
	}

	public PeerPongMessage(long nonce, RadixSystem system, int magic) {
		super(system, magic);

		this.nonce = nonce;
	}

	@Override
	public String getCommand() {
		return "peer.pong";
	}

	public long getNonce() {
		return nonce;
	}
}
