package com.radixdlt.client.core.crypto;

import com.radixdlt.client.core.address.EUID;
import com.radixdlt.client.core.atoms.RadixHash;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import org.bouncycastle.util.encoders.Base64;
import java.util.Random;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.bouncycastle.crypto.BufferedBlockCipher;
import org.bouncycastle.crypto.CipherParameters;
import org.bouncycastle.crypto.InvalidCipherTextException;
import org.bouncycastle.crypto.engines.AESEngine;
import org.bouncycastle.crypto.modes.CBCBlockCipher;
import org.bouncycastle.crypto.paddings.PKCS7Padding;
import org.bouncycastle.crypto.paddings.PaddedBufferedBlockCipher;
import org.bouncycastle.crypto.params.ECDomainParameters;
import org.bouncycastle.crypto.params.ECPublicKeyParameters;
import org.bouncycastle.crypto.params.KeyParameter;
import org.bouncycastle.crypto.params.ParametersWithIV;
import org.bouncycastle.crypto.signers.ECDSASigner;
import org.bouncycastle.math.ec.ECPoint;
import com.radixdlt.client.core.util.Base64Encoded;

public class ECPublicKey implements Base64Encoded {
	private final byte[] publicKey;

	public ECPublicKey(byte[] publicKey) {
		this.publicKey = Arrays.copyOf(publicKey, publicKey.length);
	}

	public void copyPublicKey(byte[] dest, int destPos) {
		System.arraycopy(publicKey, 0, dest, destPos, publicKey.length);
	}

	public int length() {
		return publicKey.length;
	}

	@Override
	public String base64() {
		return Base64.toBase64String(publicKey);
	}

	@Override
	public byte[] toByteArray() {
		return Arrays.copyOf(publicKey, publicKey.length);
	}

	public EUID getUID() {
		return RadixHash.of(publicKey).toEUID();
	}

	public ECKeyPair toECKeyPair() {
		return new ECKeyPair(this);
	}

	public boolean verify(byte[] data, ECSignature signature) {
		ECDomainParameters domain = ECKeyPairGenerator.getDomain((this.length() - 1) * 8);

		ECDSASigner verifier = new ECDSASigner();
		verifier.init(false, new ECPublicKeyParameters(domain.getCurve().decodePoint(publicKey), domain));

		return verifier.verifySignature(data, signature.getR(), signature.getS());
	}

	@Override
	public int hashCode() {
		// Slow but works for now
		return base64().hashCode();
	}

	@Override
	public boolean equals(Object o) {
		if (o == null || !(o instanceof ECPublicKey)) {
			return false;
		}

		ECPublicKey publicKey = (ECPublicKey) o;

		// Slow but works for now
		return publicKey.base64().equals(this.base64());
	}

	@Override
	public String toString() {
		return base64();
	}

	ECPoint getPublicPoint() {
		int domainSize = this.publicKey[0] == 4 ? ((this.publicKey.length / 2) - 1) * 8 : (this.publicKey.length - 1) * 8;

		ECDomainParameters domain = ECKeyPairGenerator.getDomain(domainSize);

		if (domain == null) {
			throw new RuntimeException("Invalid domain key size " + ((this.publicKey.length - 1) * 8));
		}

		return domain.getCurve().decodePoint(this.publicKey);
	}

	byte[] calculateMAC(byte[] salt, byte[] iv, ECPublicKey ephemeralPublicKey, byte[] encrypted) throws IOException {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		DataOutputStream outputStream = new DataOutputStream(baos);

		outputStream.write(iv);
		//outputStream.writeByte(ephemeralPublicKey.length());
		outputStream.write(ephemeralPublicKey.publicKey);
		//outputStream.writeInt(encrypted.length);
		outputStream.write(encrypted);

		try {
			Mac mac = Mac.getInstance("HmacSHA256", "BC");
			mac.init(new SecretKeySpec(salt, "HmacSHA256"));
			return mac.doFinal(baos.toByteArray());
		} catch (GeneralSecurityException e) {
			throw new IOException(e);
		}
	}

	byte[] crypt(boolean encrypt, byte[] iv, byte[] data, byte[] keyE) {
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
			throw new RuntimeException(e);
		}
	}

	public byte[] encrypt(byte[] data) {
		try {
			Random rand = new SecureRandom();

			// 1. The destination is this.getPublicKey()
			// 2. Generate 16 random bytes using a secure random number generator. Call them IV
			byte[] iv = new byte[16];
			rand.nextBytes(iv);

			// 3. Generate a new ephemeral EC key pair
			ECKeyPair ephemeral = ECKeyPairGenerator.newInstance().generateKeyPair((publicKey.length - 1) * 8);

			// 4. Do an EC point multiply with this.getPublicKey() and ephemeral private key. This gives you a point M.
			ECPoint m = getPublicPoint().multiply(new BigInteger(1, ephemeral.getPrivateKey())).normalize();

			// 5. Use the X component of point M and calculate the SHA512 hash H.
			byte[] h = RadixHash.sha512of(m.getXCoord().getEncoded()).toByteArray();

			// 6. The first 32 bytes of H are called key_e and the last 32 bytes are called key_m.
			byte[] keyE = Arrays.copyOfRange(h, 0, 32);
			byte[] keyM = Arrays.copyOfRange(h, 32, 64);

			// 7. Pad the input text to a multiple of 16 bytes, in accordance to PKCS7.
			// 8. Encrypt the data with AES-256-CBC, using IV as initialization vector,
			// key_e as encryption key and the padded input text as payload. Call the output cipher text.
			byte[] encrypted = crypt(true, iv, data, keyE);

			// 9. Calculate a 32 byte MAC with HMACSHA256, using key_m as salt and
			// IV + ephemeral.pub + cipher text as data. Call the output MAC.
			byte[] mac = calculateMAC(keyM, iv, ephemeral.getPublicKey(), encrypted);

			// 10. Write out the encryption result IV + ephemeral.pub + encrypted + MAC
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			DataOutputStream outputStream = new DataOutputStream(baos);
			outputStream.write(iv);
			outputStream.writeByte(ephemeral.getPublicKey().length());
			outputStream.write(ephemeral.getPublicKey().publicKey);
			outputStream.writeInt(encrypted.length);
			outputStream.write(encrypted);
			outputStream.write(mac);

			return baos.toByteArray();
		} catch (IOException ioex) {
			throw new RuntimeException(ioex);
		}
	}
}
