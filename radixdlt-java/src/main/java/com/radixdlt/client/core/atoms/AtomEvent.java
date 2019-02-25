package com.radixdlt.client.core.atoms;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.radix.serialization2.DsonOutput;
import org.radix.serialization2.DsonOutput.Output;
import org.radix.serialization2.SerializerId2;
import org.radix.serialization2.client.SerializableObject;

@SerializerId2("ATOMEVENT")
public class AtomEvent extends SerializableObject {
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
