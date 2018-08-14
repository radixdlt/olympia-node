package com.radixdlt.client.core.identity;

import com.radixdlt.client.application.EncryptedData;
import com.radixdlt.client.core.address.EUID;
import com.radixdlt.client.core.atoms.Atom;
import com.radixdlt.client.core.atoms.EncryptedPayload;
import com.radixdlt.client.core.atoms.UnsignedAtom;
import com.radixdlt.client.core.crypto.CryptoException;
import com.radixdlt.client.core.crypto.ECKeyPair;
import com.radixdlt.client.core.crypto.ECKeyPairGenerator;
import com.radixdlt.client.core.crypto.ECPublicKey;
import com.radixdlt.client.core.crypto.ECSignature;
import com.radixdlt.client.core.crypto.EncryptedPrivateKey;
import com.radixdlt.client.core.crypto.MacMismatchException;
import io.reactivex.Single;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public class SimpleRadixIdentity implements RadixIdentity {
	private final ECKeyPair myKey;

	public SimpleRadixIdentity(File myKeyFile) throws IOException {
		if (myKeyFile.exists()) {
			myKey = ECKeyPair.fromFile(myKeyFile);
		} else {
			myKey = ECKeyPairGenerator.newInstance().generateKeyPair();
			try (FileOutputStream io = new FileOutputStream(myKeyFile)) {
				io.write(myKey.getPrivateKey());
			}
		}
	}

	public SimpleRadixIdentity(String fileName) throws IOException {
		this(new File(fileName));
	}

	public SimpleRadixIdentity() throws IOException {
		this("my.key");
	}

	public Atom synchronousSign(UnsignedAtom atom) {
		ECSignature signature = myKey.sign(atom.getHash().toByteArray());
		EUID signatureId = myKey.getUID();
		return atom.sign(signature, signatureId);
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
	public Single<byte[]> decrypt(EncryptedData data) {
		for (EncryptedPrivateKey protector : data.getProtectors()) {
			// TODO: remove exception catching
			try {
				return Single.just(myKey.decrypt(data.getEncrypted(), protector));
			} catch (MacMismatchException e) {
			}
		}
		return Single.error(new CryptoException("Cannot decrypt"));
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
