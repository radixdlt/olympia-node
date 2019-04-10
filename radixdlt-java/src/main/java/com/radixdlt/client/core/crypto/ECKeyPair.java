package com.radixdlt.client.core.crypto;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.security.KeyFactory;
import java.util.Arrays;

import org.bouncycastle.crypto.digests.SHA256Digest;
import org.bouncycastle.crypto.params.ECDomainParameters;
import org.bouncycastle.crypto.params.ECPrivateKeyParameters;
import org.bouncycastle.crypto.signers.DSAKCalculator;
import org.bouncycastle.crypto.signers.ECDSASigner;
import org.bouncycastle.crypto.signers.HMacDSAKCalculator;
import org.bouncycastle.crypto.signers.RandomDSAKCalculator;
import org.bouncycastle.jce.interfaces.ECPrivateKey;
import org.bouncycastle.jce.spec.ECParameterSpec;
import org.bouncycastle.jce.spec.ECPrivateKeySpec;
import org.bouncycastle.jce.spec.ECPublicKeySpec;
import org.bouncycastle.math.ec.ECPoint;
import org.radix.common.ID.EUID;
import org.radix.serialization2.DsonOutput;
import org.radix.serialization2.DsonOutput.Output;
import org.radix.serialization2.SerializerId2;
import org.radix.serialization2.client.SerializableObject;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.radixdlt.client.core.atoms.RadixHash;


@SerializerId2("crypto.ec_key_pair")
public class ECKeyPair extends SerializableObject {
	@JsonProperty("public")
	@DsonOutput(Output.ALL)
	private ECPublicKey publicKey;

	@JsonProperty("private")
	@DsonOutput(Output.PERSIST)
	private byte[] privateKey;

	ECKeyPair() {
		// No-arg constructor for serializer
	}

	public ECKeyPair(ECPublicKey publicKey) {
		this.publicKey = publicKey;
		this.privateKey = null;
	}

	public ECKeyPair(byte[] publicKey, byte[] privateKey) {
		this.publicKey = new ECPublicKey(publicKey);
		this.privateKey = Arrays.copyOf(privateKey, privateKey.length);
	}

	public ECKeyPair(byte[] privateKey) {
		this.privateKey = Arrays.copyOf(privateKey, privateKey.length);

		ECPrivateKey ecPrivateKey;
		try {
			ECDomainParameters domain = ECKeyPairGenerator.getDomain(((this.privateKey.length - 1) * 8));
			ECPrivateKeySpec privateKeySpec = new ECPrivateKeySpec(
				// Set sign to positive to stop BigInteger interpreting high bit as sign
				new BigInteger(1, this.privateKey),
				new ECParameterSpec(domain.getCurve(), domain.getG(), domain.getN(), domain.getH())
			);
			ecPrivateKey = (ECPrivateKey) KeyFactory.getInstance("EC", "BC").generatePrivate(privateKeySpec);
		} catch (Exception e) {
			throw new RuntimeException(e.getMessage());
		}

		try {
			ECDomainParameters domain = ECKeyPairGenerator.getDomain((this.privateKey.length - 1) * 8);
			ECPublicKeySpec publicKeySpec = new ECPublicKeySpec(
				domain.getG().multiply(ecPrivateKey.getD()),
				new ECParameterSpec(domain.getCurve(), domain.getG(), domain.getN(), domain.getH())
			);
			this.publicKey = new ECPublicKey(
				((org.bouncycastle.jce.interfaces.ECPublicKey) KeyFactory.getInstance("EC", "BC")
					.generatePublic(publicKeySpec)).getQ().getEncoded(true));
		} catch (Exception e) {
			throw new RuntimeException(e.getMessage());
		}
	}

	public static ECKeyPair fromFile(File file) throws IOException {
		try (BufferedInputStream io = new BufferedInputStream(new FileInputStream(file))) {
			byte[] privateKey = new byte[32];
			int len = io.read(privateKey, 0, 32);

			if (len < 32) {
				throw new IllegalStateException("Private Key file must be 32 bytes");
			}

			return new ECKeyPair(privateKey);
		}
	}

	public EUID getUID() {
		return publicKey.getUID();
	}

	public EncryptedPrivateKey encryptPrivateKey(ECPublicKey publicKey) {
		if (privateKey == null) {
			throw new IllegalStateException("This key pair does not contain a private key.");
		}

		return new EncryptedPrivateKey(publicKey.encrypt(privateKey));
	}


	public ECPublicKey getPublicKey() {
		return publicKey;
	}

	public byte[] getPrivateKey() {
		if (privateKey == null) {
			throw new IllegalStateException("This key pair does not contain a private key.");
		}

		return Arrays.copyOf(privateKey, privateKey.length);
	}

	public ECSignature sign(byte[] data) {
		boolean enforceLowS = true;
		boolean useDeterministicSignatures = false;
		return sign(data, enforceLowS, useDeterministicSignatures);
	}

	/**
	 * Signs data using the ECPrivateKey resulting in an ECDSA signature.
	 *
	 * @param data The data to sign
	 * @param enforceLowS If signature should enforce low values of signature part `S`, according to
	 * <a href="https://github.com/bitcoin/bips/blob/master/bip-0062.mediawiki#Low_S_values_in_signatures">BIP-62</a>
	 * @param beDeterministic If signing should use randomness or be deterministic according to
	 * <a href="https://tools.ietf.org/html/rfc6979">RFC6979</a>.
	 * @return An ECDSA Signature.
	 */
	public ECSignature sign(byte[] data, boolean enforceLowS, boolean beDeterministic) {
		ECDomainParameters domain = ECKeyPairGenerator.getDomain((getPublicKey().length() - 1) * 8);

		final DSAKCalculator kCalculator = beDeterministic ? new HMacDSAKCalculator(new SHA256Digest()) : new RandomDSAKCalculator();
		ECDSASigner signer = new ECDSASigner(kCalculator);
		signer.init(true, new ECPrivateKeyParameters(new BigInteger(1, getPrivateKey()), domain));
		BigInteger[] components = signer.generateSignature(data);

		BigInteger r = components[0];
		BigInteger s = components[1];

		BigInteger curveOrder = domain.getN();
		BigInteger halvCurveOrder = curveOrder.shiftRight(1);

		boolean sIsLow = s.compareTo(halvCurveOrder) <= 0;

		if (enforceLowS && !sIsLow) {
			s = curveOrder.subtract(s);
		}

		return new ECSignature(r, s);
	}

	public byte[] decrypt(byte[] data, EncryptedPrivateKey sharedKey) throws CryptoException {
		if (privateKey == null) {
			throw new IllegalStateException("This key does not contain a private key.");
		}

		byte[] privateKey = decrypt(sharedKey.toByteArray());
		final ECKeyPair sharedPrivateKey = new ECKeyPair(privateKey);

		return sharedPrivateKey.decrypt(data);
	}

	public byte[] decrypt(byte[] data) throws CryptoException {
		if (privateKey == null) {
			throw new IllegalStateException("This key does not contain a private key.");
		}

		try {
			DataInputStream inputStream = new DataInputStream(new ByteArrayInputStream(data));

			// 1. Read the IV
			byte[] iv = new byte[16];
			inputStream.readFully(iv);

			// 2. Read the ephemeral public key
			int publicKeySize = inputStream.readUnsignedByte();
			byte[] publicKeyRaw = new byte[publicKeySize];
			inputStream.readFully(publicKeyRaw);
			ECPublicKey ephemeral = new ECPublicKey(publicKeyRaw);

			// 3. Do an EC point multiply with this.getPrivateKey() and ephemeral public key. This gives you a point M.
			ECPoint m = ephemeral.getPublicPoint().multiply(new BigInteger(1, getPrivateKey())).normalize();

			// 4. Use the X component of point M and calculate the SHA512 hash H.
			byte[] h = RadixHash.sha512of(m.getXCoord().getEncoded()).toByteArray();

			// 5. The first 32 bytes of H are called key_e and the last 32 bytes are called key_m.
			byte[] keyE = Arrays.copyOfRange(h, 0, 32);
			byte[] keyM = Arrays.copyOfRange(h, 32, 64);

			// 6. Read encrypted data
			byte[] encrypted = new byte[inputStream.readInt()];
			inputStream.readFully(encrypted);

			// 6. Read MAC
			byte[] mac = new byte[32];
			inputStream.readFully(mac);

			// 7. Compare MAC with MAC'. If not equal, decryption will fail.
			byte[] pkMac = publicKey.calculateMAC(keyM, iv, ephemeral, encrypted);
			if (!Arrays.equals(mac, pkMac)) {
				throw new MacMismatchException(pkMac, mac);
			}

			// 8. Decrypt the cipher text with AES-256-CBC, using IV as initialization vector, key_e as decryption key
			//    and the cipher text as payload. The output is the padded input text.
			return publicKey.crypt(false, iv, encrypted, keyE);
		} catch (IOException e) {
			throw new CryptoException("Failed to decrypt", e);
		}
	}

	@Override
	public int hashCode() {
		return this.publicKey.hashCode();
	}

	@Override
	public boolean equals(Object o) {
		if (o == null || !(o instanceof ECKeyPair)) {
			return false;
		}

		ECKeyPair ecKeyPair = (ECKeyPair) o;

		return this.publicKey.equals(ecKeyPair.getPublicKey());
	}
}
