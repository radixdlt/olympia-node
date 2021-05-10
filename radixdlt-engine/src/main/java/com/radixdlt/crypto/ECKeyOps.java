package com.radixdlt.crypto;

import org.bouncycastle.crypto.InvalidCipherTextException;
import org.bouncycastle.crypto.agreement.ECDHBasicAgreement;
import org.bouncycastle.crypto.params.ECPrivateKeyParameters;
import org.bouncycastle.crypto.params.ECPublicKeyParameters;

import java.math.BigInteger;

/**
 * An interface used for actions that require access to node's private key
 */
public interface ECKeyOps {
	/**
	 * Calculates an ECDH agreement using node's private key
	 */
	BigInteger ecdhAgreement(ECPublicKey publicKey);

	/**
	 * Decrypts a message using node's private key
	 */
	byte[] eciesDecrypt(byte[] cipher, byte[] macData) throws InvalidCipherTextException;

	static ECKeyOps fromKeyPair(ECKeyPair keyPair) {
		return new ECKeyOps() {
			@Override
			public BigInteger ecdhAgreement(ECPublicKey publicKey) {
				final var agreement = new ECDHBasicAgreement();
				agreement.init(new ECPrivateKeyParameters(new BigInteger(1, keyPair.getPrivateKey()), ECKeyUtils.domain()));
				return agreement.calculateAgreement(new ECPublicKeyParameters(publicKey.getEcPoint(), ECKeyUtils.domain()));
			}

			@Override
			public byte[] eciesDecrypt(byte[] cipher, byte[] macData) throws InvalidCipherTextException {
				return ECIESCoder.decrypt(new BigInteger(1, keyPair.getPrivateKey()), cipher, macData);
			}
		};
	}
}
