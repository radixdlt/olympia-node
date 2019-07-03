package org.radix.atoms.sync.messages;

import org.radix.network.messaging.Message;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.SerializerId2;
import org.radix.time.TemporalProof;

import com.fasterxml.jackson.annotation.JsonProperty;

@SerializerId2("atom.temporal_proof")
public class AtomTemporalProofMessage extends Message
{
	@JsonProperty("temporal_proof")
	@DsonOutput(Output.ALL)
	private TemporalProof	temporalProof;

	public AtomTemporalProofMessage()
	{
		super();
	}

	public AtomTemporalProofMessage(TemporalProof temporalProof)
	{
		super();

		this.temporalProof = temporalProof;
	}

	@Override
	public String getCommand()
	{
		return "atom.temporal_proof";
	}

	public TemporalProof getTemporalProof()
	{
		return this.temporalProof;
	}
}
