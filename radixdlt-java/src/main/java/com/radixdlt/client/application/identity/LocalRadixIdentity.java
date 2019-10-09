package com.radixdlt.client.application.identity;

import com.radixdlt.client.core.atoms.Atom;
import com.radixdlt.client.core.crypto.CryptoException;
import com.radixdlt.client.core.crypto.ECKeyPair;
import com.radixdlt.client.core.crypto.ECPublicKey;
import com.radixdlt.client.core.crypto.ECSignature;
import com.radixdlt.client.core.crypto.EncryptedPrivateKey;
import io.reactivex.Single;
import org.radix.common.ID.EUID;

public class LocalRadixIdentity implements RadixIdentity {
	private final ECKeyPair myKey;

	LocalRadixIdentity(ECKeyPair myKey) {
		this.myKey = myKey;
	}

	public Atom syncAddSignature(Atom atom) {
		ECSignature signature = myKey.sign(atom.getHash().toByteArray());
		EUID signatureId = myKey.getUID();
		return atom.addSignature(signatureId, signature);
	}

	@Override
	public Single<Atom> addSignature(Atom atom) {
		return Single.create(emitter -> {
			final Atom signedAtom = syncAddSignature(atom);
			emitter.onSuccess(signedAtom);
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
