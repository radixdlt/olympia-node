package com.radixdlt.client.application.identity;

import com.radixdlt.client.application.objects.Data;
import com.radixdlt.client.application.objects.UnencryptedData;
import com.radixdlt.client.core.address.EUID;
import com.radixdlt.client.core.atoms.Atom;
import com.radixdlt.client.core.atoms.UnsignedAtom;
import com.radixdlt.client.core.crypto.CryptoException;
import com.radixdlt.client.core.crypto.ECKeyPair;
import com.radixdlt.client.core.crypto.ECPublicKey;
import com.radixdlt.client.core.crypto.ECSignature;

import com.radixdlt.client.core.crypto.EncryptedPrivateKey;
import com.radixdlt.client.core.crypto.MacMismatchException;
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
	public Single<UnencryptedData> decrypt(Data data) {
		boolean encrypted = (Boolean) data.getMetaData().get("encrypted");
		if (encrypted) {
			for (EncryptedPrivateKey protector : data.getProtectors()) {
				// TODO: remove exception catching
				try {
					byte[] bytes = myKey.decrypt(data.getBytes(), protector);
					return Single.just(new UnencryptedData(bytes, data.getMetaData(), true));
				} catch (MacMismatchException e) {
				}
			}
			return Single.error(new CryptoException("Cannot decrypt"));
		} else {
			return Single.just(new UnencryptedData(data.getBytes(), data.getMetaData(), false));
		}
	}

	@Override
	public ECPublicKey getPublicKey() {
		return myKey.getPublicKey();
	}
}
