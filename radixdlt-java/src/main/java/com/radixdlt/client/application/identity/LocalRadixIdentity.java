package com.radixdlt.client.application.identity;

import org.radix.common.ID.EUID;

import com.radixdlt.client.core.atoms.Atom;
import com.radixdlt.client.core.atoms.UnsignedAtom;
import com.radixdlt.client.core.crypto.CryptoException;
import com.radixdlt.client.core.crypto.ECKeyPair;
import com.radixdlt.client.core.crypto.ECPublicKey;
import com.radixdlt.client.core.crypto.ECSignature;
import com.radixdlt.client.core.crypto.EncryptedPrivateKey;

import io.reactivex.Single;

public class LocalRadixIdentity implements RadixIdentity {
	private final ECKeyPair myKey;

	LocalRadixIdentity(ECKeyPair myKey) {
		this.myKey = myKey;
	}

	public Atom syncSign(UnsignedAtom unsignedAtom) {
		ECSignature signature = myKey.sign(unsignedAtom.getHash().toByteArray());
		EUID signatureId = myKey.getUID();
		return unsignedAtom.sign(signature, signatureId);
	}

	@Override
	public Single<Atom> sign(UnsignedAtom unsignedAtom) {
		return Single.create(emitter -> {
			final Atom atom = syncSign(unsignedAtom);
			emitter.onSuccess(atom);
		});
	}

	@Override
	public Single<UnencryptedData> decrypt(Data data) {
		boolean encrypted = (Boolean) data.getMetaData().get("encrypted");
		if (encrypted) {
			for (EncryptedPrivateKey protector : data.getEncryptor().getProtectors()) {
				// TODO: remove exception catching
				try {
					byte[] bytes = myKey.decrypt(data.getBytes(), protector);
					return Single.just(new UnencryptedData(bytes, data.getMetaData(), true));
				} catch (CryptoException e) {
					// Decryption failed, try the next one
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
