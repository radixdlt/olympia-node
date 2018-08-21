package com.radixdlt.client.core.serialization;

import com.radixdlt.client.core.atoms.Atom;
import com.radixdlt.client.core.atoms.PayloadAtom;
import java.util.Optional;

public enum SerializedAtomType {
	ATOM(PayloadAtom.class, -1231693889);

	private final Class<? extends Atom> atomClass;
	private final long serializer;

	SerializedAtomType(Class<? extends Atom> atomClass, long serializer) {
		this.atomClass = atomClass;
		this.serializer = serializer;
	}

	public Class<? extends Atom> getAtomClass() {
		return atomClass;
	}

	public long getSerializer() {
		return serializer;
	}

	public static Optional<SerializedAtomType> valueOf(Class<? extends Atom> atomClass) {
		for (SerializedAtomType atomType : SerializedAtomType.values()) {
			if (atomType.atomClass.equals(atomClass)) {
				return Optional.of(atomType);
			}
		}

		return Optional.empty();
	}

	public static Optional<SerializedAtomType> valueOf(long serializer) {
		for (SerializedAtomType atomType : SerializedAtomType.values()) {
			if (atomType.serializer == serializer) {
				return Optional.of(atomType);
			}
		}

		return Optional.empty();
	}
}
