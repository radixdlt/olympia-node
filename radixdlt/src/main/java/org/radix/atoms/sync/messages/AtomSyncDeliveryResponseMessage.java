package org.radix.atoms.sync.messages;

import com.radixdlt.common.AID;

import org.radix.network.messaging.Message;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.SerializerId2;

import com.fasterxml.jackson.annotation.JsonProperty;

@SerializerId2("atom.sync.delivery.response")
public class AtomSyncDeliveryResponseMessage extends Message
{
	@JsonProperty("atom")
	@DsonOutput(Output.ALL)
	private AID atom;

	@JsonProperty("atom_fragment")
	@DsonOutput(Output.ALL)
	private byte[] atomFragment;

	@JsonProperty("fragment")
	@DsonOutput(Output.ALL)
	private int fragment;

	@JsonProperty("fragments")
	@DsonOutput(Output.ALL)
	private int fragments;

	AtomSyncDeliveryResponseMessage()
	{
		// For serializer only
	}

	public AtomSyncDeliveryResponseMessage(AID atom, byte[] atomFragment, int fragment, int fragments)
	{
		super();

		this.atom = atom;
		this.atomFragment = atomFragment;
		this.fragment = fragment;
		this.fragments = fragments;
	}

	@Override
	public String getCommand()
	{
		return "atom.sync.delivery.response";
	}

	public AID getAtom()
	{
		return this.atom;
	}

	public byte[] getAtomFragment()
	{
		return this.atomFragment;
	}

	public int getFragments()
	{
		return this.fragments;
	}

	public int getFragment()
	{
		return this.fragment;
	}
}
