package org.radix.universe.system;

import org.radix.network.messaging.SignedMessage;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.SerializerId2;
import com.radixdlt.serialization.DsonOutput.Output;

import com.fasterxml.jackson.annotation.JsonProperty;

@SerializerId2("system")
public class SystemMessage extends SignedMessage
{
	@JsonProperty("system")
	@DsonOutput(Output.ALL)
	private RadixSystem system;

	public SystemMessage()
	{
		super();

		this.system = new RadixSystem(LocalSystem.getInstance());
	}

	@Override
	public String getCommand()
	{
		return "system";
	}

	public RadixSystem getSystem()
	{
		return this.system;
	}
}
