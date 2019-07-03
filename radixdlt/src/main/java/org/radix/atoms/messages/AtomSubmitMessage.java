package org.radix.atoms.messages;

import com.radixdlt.atoms.Atom;
import org.radix.network.messaging.Message;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.SerializerId2;

import com.fasterxml.jackson.annotation.JsonProperty;

@SerializerId2("atom.submit")
public final class AtomSubmitMessage extends Message
{
	@JsonProperty("atom")
	@DsonOutput(Output.ALL)
	private Atom	atom;

	AtomSubmitMessage()
	{
		// For serializer only
	}

	public AtomSubmitMessage(Atom atom)
	{
		super();

		this.atom = atom;
	}

	@Override
	public String getCommand()
	{
		return "atom.submit";
	}

	public Atom getAtom() { return atom; }
}
