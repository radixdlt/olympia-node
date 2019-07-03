package org.radix.atoms.particles.conflict.messages;

import org.radix.atoms.particles.conflict.ParticleConflict;
import org.radix.network.messaging.Message;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.SerializerId2;

import com.fasterxml.jackson.annotation.JsonProperty;

@SerializerId2("conflict.assist.response")
public final class ConflictAssistResponseMessage extends Message
{
	@JsonProperty("conflict")
	@DsonOutput(Output.ALL)
	private ParticleConflict conflict;

	public ConflictAssistResponseMessage()
	{
		super();
	}

	public ConflictAssistResponseMessage(ParticleConflict conflict)
	{
		super();

		this.conflict = conflict;
	}

	@Override
	public String getCommand()
	{
		return "conflict.assist.response";
	}

	public ParticleConflict getConflict()
	{
		return this.conflict;
	}
}
