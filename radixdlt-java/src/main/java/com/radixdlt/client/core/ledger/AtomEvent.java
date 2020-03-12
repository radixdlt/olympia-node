package com.radixdlt.client.core.ledger;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.radixdlt.client.core.atoms.Atom;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.SerializerConstants;
import com.radixdlt.serialization.SerializerDummy;
import com.radixdlt.serialization.SerializerId2;
@SerializerId2("api.atom_event")
public class AtomEvent {

	// Placeholder for the serializer ID
	@JsonProperty(SerializerConstants.SERIALIZER_NAME)
	@DsonOutput(DsonOutput.Output.ALL)
	private SerializerDummy serializer = SerializerDummy.DUMMY;

	@JsonProperty("version")
	@DsonOutput(DsonOutput.Output.ALL)
	private short version = 100;

	public enum AtomEventType {
		STORE, DELETE
	}

	@JsonProperty("atom")
	@DsonOutput(Output.ALL)
	private Atom atom;

	private transient AtomEventType type;

	private AtomEvent() {
		this.atom = null;
		this.type = null;
	}

	public AtomEvent(Atom atom, AtomEventType type) {
		this.atom = atom;
		this.type = type;
	}

	public Atom getAtom() {
		return atom;
	}

	public AtomEventType getType() {
		return type;
	}

	@JsonProperty("type")
	@DsonOutput(Output.ALL)
	private String getTypeString() {
		return this.type.name().toLowerCase();
	}

	@JsonProperty("type")
	private void setTypeString(String type) {
		this.type = AtomEventType.valueOf(type.toUpperCase());
	}
}
