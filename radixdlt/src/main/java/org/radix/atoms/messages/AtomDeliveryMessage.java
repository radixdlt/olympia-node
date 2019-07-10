package org.radix.atoms.messages;

import org.radix.atoms.Atom;
import org.radix.network.messaging.Message;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.SerializerId2;

import com.fasterxml.jackson.annotation.JsonProperty;

@SerializerId2("atom.delivery")
public final class AtomDeliveryMessage extends Message
{
	@JsonProperty("atom")
	@DsonOutput(Output.ALL)
	private Atom	atom;

	AtomDeliveryMessage()
	{
		// For serializer only
	}

	public AtomDeliveryMessage(Atom atom)
	{
		super();

		this.atom = atom;
	}

	@Override
	public String getCommand()
	{
		return "atom.delivery";
	}

	public Atom getAtom()
	{
		return atom;
	}
}
