package com.radixdlt.client.core.atoms;

import org.radix.common.ID.EUID;

import com.radixdlt.client.core.crypto.ECSignature;

public class UnsignedAtom {
	private final Atom atom;
	public UnsignedAtom(Atom atom) {
		this.atom = atom;
	}

	public Atom getRawAtom() {
		return atom;
	}

	public RadixHash getHash() {
		return atom.getHash();
	}

	public Atom sign(ECSignature signature, EUID signatureId) {
		// TODO: Remove need to create a new object
		return atom.withSignature(signature, signatureId);
	}
}
