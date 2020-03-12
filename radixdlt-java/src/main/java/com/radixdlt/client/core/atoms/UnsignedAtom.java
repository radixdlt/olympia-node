package com.radixdlt.client.core.atoms;

import com.radixdlt.crypto.Hash;
import com.radixdlt.identifiers.EUID;

import com.radixdlt.crypto.ECDSASignature;

public class UnsignedAtom {
	private final Atom atom;
	public UnsignedAtom(Atom atom) {
		this.atom = atom;
	}

	public Atom getRawAtom() {
		return atom;
	}

	public Hash getHash() {
		return atom.getHash();
	}

	public Atom sign(ECDSASignature signature, EUID signatureId) {
		// TODO: Remove need to create a new object
		return atom.withSignature(signature, signatureId);
	}
}
