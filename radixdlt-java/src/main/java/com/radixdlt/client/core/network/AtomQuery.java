package com.radixdlt.client.core.network;

import com.google.gson.JsonObject;
import com.radixdlt.client.core.address.EUID;
import com.radixdlt.client.core.atoms.Atom;
import com.radixdlt.client.core.serialization.SerializedAtomType;
import java.util.Optional;

public class AtomQuery<T extends Atom> {
	private final EUID destination;
	private final SerializedAtomType atomType;
	private final Class<T> atomClass;

	public AtomQuery(EUID destination, Class<T> atomClass) {
		this.destination = destination;
		this.atomClass = atomClass;

		if (atomClass == Atom.class) {
			this.atomType = null;
		} else {
			Optional<SerializedAtomType> atomType = SerializedAtomType.valueOf(atomClass);
			if (!atomType.isPresent()) {
				throw new IllegalArgumentException("Cannot serialize atom class: " + atomClass);
			}
			this.atomType = atomType.get();
		}
	}

	public Optional<SerializedAtomType> getAtomType() {
		return Optional.ofNullable(atomType);
	}

	public EUID getDestination() {
		return destination;
	}

	public Class<T> getAtomClass() {
		return atomClass;
	}

	public JsonObject toJson() {
		JsonObject query = new JsonObject();
		query.addProperty("destination", destination.toString());

		if (atomType != null) {
			query.addProperty("atomSerializer", atomType.getSerializer());
		}

		return query;
	}
}
