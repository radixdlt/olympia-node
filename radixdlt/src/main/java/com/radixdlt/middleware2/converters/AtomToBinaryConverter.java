package com.radixdlt.middleware2.converters;

import com.radixdlt.common.Atom;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.Serialization;
import com.radixdlt.serialization.SerializationException;

public final class AtomToBinaryConverter {
	private final Serialization serializer;

	public AtomToBinaryConverter(Serialization serializer) {
		this.serializer = serializer;
	}

	public byte[] toLedgerEntryContent(Atom atom) {
		try {
			return serializer.toDson(atom, DsonOutput.Output.PERSIST);
		} catch (SerializationException e) {
			throw new RuntimeException(String.format("Serialization for Atom with ID: %s failed", atom.getAID()));
		}
	}

	public Atom toAtom(byte[] ledgerEntryContent) {
		try {
			return serializer.fromDson(ledgerEntryContent, Atom.class);
		} catch (SerializationException e) {
			throw new RuntimeException("Deserialization of Atom failed");
		}
	}
}
