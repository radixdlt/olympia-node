package com.radixdlt.client.core.identity;

import com.radixdlt.client.core.address.EUID;
import com.radixdlt.client.core.atoms.Atom;
import com.radixdlt.client.core.atoms.EncryptedPayload;
import com.radixdlt.client.core.atoms.UnsignedAtom;
import com.radixdlt.client.core.crypto.ECKeyPair;
import com.radixdlt.client.core.crypto.ECKeyPairGenerator;
import com.radixdlt.client.core.crypto.ECPublicKey;
import com.radixdlt.client.core.crypto.ECSignature;
import io.reactivex.Single;

// Simply generate a key pair and don't worry about saving it
public class OneTimeUseIdentity implements RadixIdentity {
	private final ECKeyPair myKey;

	public OneTimeUseIdentity() {
		myKey = ECKeyPairGenerator.newInstance().generateKeyPair();
	}

	public Atom synchronousSign(UnsignedAtom unsignedAtom) {
		ECSignature signature = myKey.sign(unsignedAtom.getHash().toByteArray());
		EUID signatureId = myKey.getUID();
		return unsignedAtom.sign(signature, signatureId);
	}

	@Override
	public Single<Atom> sign(UnsignedAtom atom) {
		return Single.create(emitter -> {
			ECSignature signature = myKey.sign(atom.getHash().toByteArray());
			EUID signatureId = myKey.getUID();
			emitter.onSuccess(atom.sign(signature, signatureId));
		});
	}

	@Override
	public Single<byte[]> decrypt(EncryptedPayload data) {
		return Single.fromCallable(() -> data.decrypt(myKey));
	}

	@Override
	public ECPublicKey getPublicKey() {
		return myKey.getPublicKey();
	}
}
