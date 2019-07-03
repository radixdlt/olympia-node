package org.radix.atoms.messages;

import com.radixdlt.common.EUID;
import org.radix.network.messaging.Message;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.SerializerId2;

import com.fasterxml.jackson.annotation.JsonProperty;

@SerializerId2("atom.fetch")
public class AtomFetchMessage extends Message
{
	@JsonProperty("atom_hid")
	@DsonOutput(Output.ALL)
	private EUID	atomHID;

	AtomFetchMessage()
	{
		// For serializer only
	}

	public AtomFetchMessage(EUID atomHID)
	{
		super();

		this.atomHID = atomHID;
	}

	@Override
	public String getCommand()
	{
		return "atom.fetch";
	}

	public EUID getAtomHID()
	{
		return this.atomHID;
	}
}
