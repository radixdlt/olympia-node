package com.radixdlt.crypto;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.security.Security;
import java.util.Arrays;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.bouncycastle.asn1.x9.X9ECParameters;
import org.bouncycastle.crypto.BufferedBlockCipher;
import org.bouncycastle.crypto.CipherParameters;
import org.bouncycastle.crypto.InvalidCipherTextException;
import org.bouncycastle.crypto.ec.CustomNamedCurves;
import org.bouncycastle.crypto.engines.AESEngine;
import org.bouncycastle.crypto.modes.CBCBlockCipher;
import org.bouncycastle.crypto.paddings.PKCS7Padding;
import org.bouncycastle.crypto.paddings.PaddedBufferedBlockCipher;
import org.bouncycastle.crypto.params.ECDomainParameters;
import org.bouncycastle.crypto.params.KeyParameter;
import org.bouncycastle.crypto.params.ParametersWithIV;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.jce.spec.ECParameterSpec;
import org.bouncycastle.math.ec.FixedPointUtil;

/**
 * Utilities used by both {@link ECPublicKey} and {@link ECKeyPair}.
 */
class ECKeyUtils {

	private ECKeyUtils() {
		throw new IllegalStateException("Can't construct");
	}

	static final SecureRandom secureRandom = new SecureRandom();
	static final X9ECParameters	curve;
	static final ECDomainParameters domain;
	static final ECParameterSpec spec;

	static {
	    Security.insertProviderAt(new BouncyCastleProvider(), 1);

	    curve = CustomNamedCurves.getByName("secp256k1");

	    domain = new ECDomainParameters(curve.getCurve(), curve.getG(), curve.getN(), curve.getH());
	    spec = new ECParameterSpec(curve.getCurve(), curve.getG(), curve.getN(), curve.getH());

        FixedPointUtil.precompute(curve.getG(), 12);
	}

	// Must be after secureRandom init
	static final KeyHandler keyHandler = new BouncyCastleKeyHandler(secureRandom, curve);

	static byte[] calculateMAC(byte[] salt, byte[] iv, ECPublicKey ephemeral, byte[] encrypted) throws IOException {
		byte[] ephemeralPubKey = ephemeral.getBytes();
		int bytesLen = iv.length + encrypted.length + ephemeralPubKey.length;
		byte[] bytes = new byte[bytesLen];
		System.arraycopy(iv, 0, bytes, 0, iv.length);
		System.arraycopy(ephemeralPubKey, 0, bytes, iv.length, ephemeralPubKey.length);
		System.arraycopy(encrypted, 0, bytes, iv.length + ephemeralPubKey.length, encrypted.length);

		try {
			Mac mac = Mac.getInstance("HmacSHA256", "BC");
	        mac.init(new SecretKeySpec(salt, "HmacSHA256"));
	        return mac.doFinal(bytes);
		} catch (GeneralSecurityException e) {
			throw new IOException(e);
	    }
	}

	static byte[] crypt(boolean encrypt, byte[] iv, byte[] data, byte[] keyE) throws CryptoException {
		try {
	        BufferedBlockCipher cipher = new PaddedBufferedBlockCipher(new CBCBlockCipher(new AESEngine()), new PKCS7Padding());
	        CipherParameters params = new ParametersWithIV(new KeyParameter(keyE), iv);
	        cipher.init(encrypt, params);

	        byte[] buffer = new byte[cipher.getOutputSize(data.length)];
	        int length = cipher.processBytes(data, 0, data.length, buffer, 0);
	        length += cipher.doFinal(buffer, length);
            if (length < buffer.length) {
                return Arrays.copyOfRange(buffer, 0, length);
            }
            return buffer;
        } catch (InvalidCipherTextException e) {
            throw new CryptoException(e);
        }
    }
}
