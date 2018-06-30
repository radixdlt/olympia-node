package com.radixdlt.client.core.identity;

import com.radixdlt.client.core.crypto.CryptoException;
import io.reactivex.Single;

public final class RadixIdentities {
	private RadixIdentities() {
	}

	public static <T> Single<T> decrypt(RadixIdentity identity, Decryptable<T> decryptable) {
		return identity.decrypt(decryptable.getEncrypted())
			.map(decryptable::deserialize)
			.onErrorResumeNext(
				throwable ->
					Single.error(new CryptoException("Unable to decrypt " + decryptable + " " + throwable.toString())
				)
			);
	}
}
