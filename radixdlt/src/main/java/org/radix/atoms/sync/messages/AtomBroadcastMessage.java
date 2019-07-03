package org.radix.atoms.sync.messages;

import com.radixdlt.common.AID;
import org.radix.network.messaging.Message;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.SerializerId2;

import com.fasterxml.jackson.annotation.JsonProperty;

@SerializerId2("atom.broadcast")
public class AtomBroadcastMessage extends Message
{
	@JsonProperty("atom_hid")
	@DsonOutput(Output.ALL)
	private AID atomHID;

	AtomBroadcastMessage()
	{
		// Serializer only
	}

	public AtomBroadcastMessage(AID atomHID)
	{
		super();

		this.atomHID = atomHID;
	}

	@Override
	public String getCommand()
	{
		return "atom.broadcast";
	}

	public AID getAtomHID()
	{
		return this.atomHID;
	}
}
