package com.radixdlt.client.core.serialization;

import com.radixdlt.client.core.atoms.AssetAtom;
import com.radixdlt.client.core.atoms.Atom;
import com.radixdlt.client.core.atoms.ApplicationPayloadAtom;
import com.radixdlt.client.core.atoms.NullAtom;
import com.radixdlt.client.core.atoms.TransactionAtom;
import java.util.Optional;

public enum SerializedAtomType {
	ASSET(AssetAtom.class, 62583504L),
	TRANSACTION(TransactionAtom.class, -760130L),
	NULL(NullAtom.class, -1123323048L),
	MESSAGE(ApplicationPayloadAtom.class, -2040291185L);

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
