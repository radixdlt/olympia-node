/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Permission is hereby granted, free of charge, to any person obtaining a
 * copy of this software and associated documentation files (the “Software”),
 * to deal in the Software without restriction, including without limitation
 * the rights to use, copy, modify, merge, publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS
 * OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL
 * THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER
 * DEALINGS IN THE SOFTWARE.
 */

package com.radixdlt.client.application.identity;

import com.radixdlt.crypto.encryption.EncryptedPrivateKey;
import com.radixdlt.crypto.exception.CryptoException;
import com.radixdlt.identifiers.EUID;

import com.radixdlt.atom.Atom;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.crypto.ECDSASignature;

import io.reactivex.Single;

public class LocalRadixIdentity implements RadixIdentity {
	private final ECKeyPair myKey;

	LocalRadixIdentity(ECKeyPair myKey) {
		this.myKey = myKey;
	}

	public Atom syncAddSignature(Atom atom) {
		ECDSASignature signature = myKey.sign(atom.getHash().asBytes());
		EUID signatureId = myKey.euid();
		atom.setSignature(signatureId, signature);
		return atom;
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
