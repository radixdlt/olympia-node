package com.radixdlt.crypto;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
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
import org.bouncycastle.jce.spec.ECParameterSpec;
import org.bouncycastle.math.ec.FixedPointUtil;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.primitives.UnsignedBytes;
import com.radixdlt.utils.Bytes;

/**
 * Utilities used by both {@link ECPublicKey} and {@link ECKeyPair}.
 */
class ECKeyUtils {

	private ECKeyUtils() {
		throw new IllegalStateException("Can't construct");
	}

	static final SecureRandom secureRandom = new SecureRandom();
	private static final X9ECParameters curve;
	static final ECDomainParameters domain;
	static final ECParameterSpec spec;
	private static final byte[] order;

	static {
	    curve = CustomNamedCurves.getByName("secp256k1");

	    domain = new ECDomainParameters(curve.getCurve(), curve.getG(), curve.getN(), curve.getH());
	    spec = new ECParameterSpec(curve.getCurve(), curve.getG(), curve.getN(), curve.getH());

	    order = adjustArray(domain.getN().toByteArray(), ECKeyPair.BYTES);

        FixedPointUtil.precompute(curve.getG());
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
			Mac mac = Mac.getInstance("HmacSHA256");
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

	static void validatePrivate(byte[] privateKey) throws CryptoException {
		if (privateKey == null) {
			throw new CryptoException("Private key is null");
		}

		if (privateKey.length != ECKeyPair.BYTES) {
			throw new CryptoException("Private key is invalid length: " + privateKey.length);
		}

		if (greaterOrEqualOrder(privateKey)) {
			throw new CryptoException("Private key is greater than or equal to curve order");
		}

		int pklen = privateKey.length;
		if (allZero(privateKey, 0, pklen - 1)) {
			byte lastByte = privateKey[pklen - 1];
			if (lastByte == 0) {
				throw new CryptoException("Private key is zero");
			}
			if (lastByte == 1) {
				throw new CryptoException("Private key is one");
			}
		}
	}

	static byte[] adjustArray(byte[] array, int length) {
		if (length == array.length) {
			// Length is fine
			return array;
		}
		if (length > array.length) {
			// Needs zero padding at front
			byte[] result = new byte[length];
			System.arraycopy(array, 0, result, length - array.length, array.length);
			return result;
		}
		// Need to drop zeros at front -> error if dropped bytes are not zero
		int offset = 0;
		while (array.length - offset > length) {
			if (array[offset] != 0) {
				throw new IllegalArgumentException(String.format("Array is greater than %s bytes: %s", length, Bytes.toHexString(array)));
			}
			offset += 1;
		}
		// Now return length bytes from offset within array
		return Arrays.copyOfRange(array, offset, offset + length);
	}

	@VisibleForTesting
	static boolean greaterOrEqualOrder(byte[] privateKey) {
		if (privateKey.length != order.length) {
			throw new IllegalArgumentException("Invalid private key");
		}
		return UnsignedBytes.lexicographicalComparator().compare(order, privateKey) <= 0;
	}

	private static boolean allZero(byte[] bytes, int offset, int len) {
		for (int i = 0; i < len; ++i) {
			if (bytes[offset + i] != 0) {
				return false;
			}
		}
		return true;
	}
}
