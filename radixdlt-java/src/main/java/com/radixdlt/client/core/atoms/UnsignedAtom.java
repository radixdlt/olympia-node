package com.radixdlt.client.core.atoms;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.radixdlt.client.core.crypto.ECSignature;
import org.radix.common.ID.EUID;

public class UnsignedAtom {
	private final transient Atom unsignedAtom;

	public UnsignedAtom(ImmutableList<ParticleGroup> particleGroups, ImmutableMap<String, String> metaData) {
		this.unsignedAtom = Atom.create(particleGroups, metaData);
	}

	public RadixHash getHash() {
		return unsignedAtom.getHash();
	}

	public Atom sign(ECSignature signature, EUID signatureId) {
		return unsignedAtom.addSignature(signature, signatureId);
	}
}
