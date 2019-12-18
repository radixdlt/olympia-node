package org.radix.universe.system;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.SerializerId2;
import org.radix.network.messaging.SignedMessage;

@SerializerId2("system")
public class SystemMessage extends SignedMessage {
	@JsonProperty("system")
	@DsonOutput(Output.ALL)
	private RadixSystem system;

	protected SystemMessage() {
		// for serializer
	}

	public SystemMessage(RadixSystem system) {
		this.system = system;
	}

	@Override
	public String getCommand() {
		return "system";
	}

	public RadixSystem getSystem() {
		return this.system;
	}
}
