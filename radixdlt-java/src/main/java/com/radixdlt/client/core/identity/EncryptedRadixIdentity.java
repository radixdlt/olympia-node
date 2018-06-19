package com.radixdlt.client.core.identity;

import com.radixdlt.client.core.address.EUID;
import com.radixdlt.client.core.atoms.Atom;
import com.radixdlt.client.core.atoms.EncryptedPayload;
import com.radixdlt.client.core.atoms.UnsignedAtom;
import com.radixdlt.client.core.crypto.ECKeyPair;
import com.radixdlt.client.core.crypto.ECPublicKey;
import com.radixdlt.client.core.crypto.ECSignature;

import java.io.File;

import io.reactivex.Single;

public class EncryptedRadixIdentity implements RadixIdentity {
	private final ECKeyPair myKey;

	public EncryptedRadixIdentity(String password, File myKeyFile) throws Exception {
		if (myKeyFile.exists()) {
			myKey = getECKeyPair(password, myKeyFile);
		} else {
			PrivateKeyEncrypter.createEncryptedPrivateKeyFile(password, myKeyFile.getPath());
			myKey = getECKeyPair(password, myKeyFile);
		}
	}

	public EncryptedRadixIdentity(String password, String fileName) throws Exception {
		this(password, new File(fileName));
	}

	public EncryptedRadixIdentity(String password) throws Exception {
		this(password, "my_encrypted.key");
	}

	public Atom synchronousSign(UnsignedAtom atom) {
		ECSignature signature = myKey.sign(atom.getHash().toByteArray());
		EUID signatureId = myKey.getUID();
		return atom.sign(signature, signatureId);
	}

	private ECKeyPair getECKeyPair(String password, File myKeyfile) throws Exception {
		return new ECKeyPair(PrivateKeyEncrypter.decryptPrivateKeyFile(password, myKeyfile.getPath()));
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
