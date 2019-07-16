package org.radix.atoms.particles.conflict.messages;

import com.radixdlt.atoms.SpunParticle;
import com.radixdlt.common.EUID;
import org.radix.common.ID.ID;
import org.radix.network.messaging.Message;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.SerializerId2;

import com.fasterxml.jackson.annotation.JsonProperty;

@SerializerId2("conflict.assist.request")
public class ConflictAssistRequestMessage extends Message implements ID {
	@JsonProperty("particle")
	@DsonOutput(Output.ALL)
	private SpunParticle particle;

	public ConflictAssistRequestMessage() {
		super();
	}

	public ConflictAssistRequestMessage(SpunParticle particle) {
		super();

		this.particle = particle;
	}

	@Override
	public String getCommand() {
		return "conflict.assist.request";
	}

	public SpunParticle getSpunParticle() {
		return this.particle;
	}

	@Override
	public EUID getUID() {
		return this.particle.getParticle().getHID();
	}

	@Override
	public void setUID(EUID id) {
		throw new UnsupportedOperationException("UID can not be set on ConflictAssistMessage");
	}
}
