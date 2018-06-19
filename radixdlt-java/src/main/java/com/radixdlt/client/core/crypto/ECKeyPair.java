package com.radixdlt.client.core.crypto;

import com.google.gson.annotations.SerializedName;
import com.radixdlt.client.core.address.EUID;
import com.radixdlt.client.core.atoms.RadixHash;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.security.KeyFactory;
import java.util.Arrays;
import org.bouncycastle.crypto.params.ECDomainParameters;
import org.bouncycastle.crypto.params.ECPrivateKeyParameters;
import org.bouncycastle.crypto.signers.ECDSASigner;
import org.bouncycastle.jce.interfaces.ECPrivateKey;
import org.bouncycastle.jce.spec.ECParameterSpec;
import org.bouncycastle.jce.spec.ECPrivateKeySpec;
import org.bouncycastle.jce.spec.ECPublicKeySpec;
import org.bouncycastle.math.ec.ECPoint;

public class ECKeyPair {
	@SerializedName("public")
	private final ECPublicKey publicKey;
	private final transient byte[] privateKey;

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
		try
		{
			ECDomainParameters domain = ECKeyPairGenerator.getDomain(((this.privateKey.length-1)*8));
			ECPrivateKeySpec privateKeySpec = new ECPrivateKeySpec(new BigInteger(1, this.privateKey), new ECParameterSpec(domain.getCurve(), domain.getG(), domain.getN(), domain.getH()));
			ecPrivateKey = (ECPrivateKey) KeyFactory.getInstance("EC", "BC").generatePrivate(privateKeySpec);
		}
		catch (Exception e)
		{
			throw new RuntimeException(e.getMessage());
		}

		try
		{
			ECDomainParameters domain = ECKeyPairGenerator.getDomain((this.privateKey.length-1)*8);
			ECPublicKeySpec publicKeySpec = new ECPublicKeySpec(domain.getG().multiply (ecPrivateKey.getD()), new ECParameterSpec(domain.getCurve(), domain.getG(), domain.getN(), domain.getH()));
			this.publicKey = new ECPublicKey(((org.bouncycastle.jce.interfaces.ECPublicKey) KeyFactory.getInstance("EC", "BC").generatePublic(publicKeySpec)).getQ().getEncoded(true));
		}
		catch (Exception e)
		{
			throw new RuntimeException(e.getMessage());
		}
	}

	public ECKeyPair(File file) throws IOException {
		FileInputStream io = new FileInputStream(file);
		this.privateKey = new byte[32];
		io.read(this.privateKey);

		ECPrivateKey privateKey;

		try
		{
			ECDomainParameters domain = ECKeyPairGenerator.getDomain(((this.privateKey.length-1)*8));
			ECPrivateKeySpec privateKeySpec = new ECPrivateKeySpec(new BigInteger(1, this.privateKey), new ECParameterSpec(domain.getCurve(), domain.getG(), domain.getN(), domain.getH()));
			privateKey = (ECPrivateKey) KeyFactory.getInstance("EC", "BC").generatePrivate(privateKeySpec);
		}
		catch (Exception e)
		{
			throw new RuntimeException(e.getMessage());
		}

		try
		{
			ECDomainParameters domain = ECKeyPairGenerator.getDomain((this.privateKey.length-1)*8);
			ECPublicKeySpec publicKeySpec = new ECPublicKeySpec(domain.getG().multiply (privateKey.getD()), new ECParameterSpec(domain.getCurve(), domain.getG(), domain.getN(), domain.getH()));
			this.publicKey = new ECPublicKey(((org.bouncycastle.jce.interfaces.ECPublicKey) KeyFactory.getInstance("EC", "BC").generatePublic(publicKeySpec)).getQ().getEncoded(true));
		}
		catch (Exception e)
		{
			throw new RuntimeException(e.getMessage());
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
		ECDomainParameters domain = ECKeyPairGenerator.getDomain((getPublicKey().length() - 1) * 8);
		ECDSASigner signer = new ECDSASigner ();
		signer.init (true, new ECPrivateKeyParameters(new BigInteger(1, getPrivateKey()), domain));
		BigInteger[] components = signer.generateSignature (data);
		ECSignature signature = new ECSignature(components[0], components[1]);
		return signature;
	}

	public byte[] decrypt(byte[] data, EncryptedPrivateKey sharedKey) throws MacMismatchException {
		if (privateKey == null) {
			throw new IllegalStateException("This key does not contain a private key.");
		}

		byte[] privateKey = decrypt(sharedKey.toByteArray());
		final ECKeyPair sharedPrivateKey = new ECKeyPair(privateKey);

		try {
			return sharedPrivateKey.decrypt(data);
		} catch (MacMismatchException e) {
			System.out.println(e.getExpectedBase64() + " " + e.getActualBase64());
			throw new IllegalStateException("Unable to decrypt with shared private key.");
		}
	}

	public byte[] decrypt(byte[] data) throws MacMismatchException
	{
		if (privateKey == null) {
			throw new IllegalStateException("This key does not contain a private key.");
		}

		try
		{
			DataInputStream inputStream = new DataInputStream(new ByteArrayInputStream(data));


			// 1. Read the IV
			byte[] IV = new byte[16];
			inputStream.readFully(IV);


			// 2. Read the ephemeral public key
			int publicKeySize = inputStream.readUnsignedByte();
			byte[] publicKeyRaw = new byte[publicKeySize];
			inputStream.readFully(publicKeyRaw);
			ECPublicKey ephemeral = new ECPublicKey(publicKeyRaw);

			// 3. Do an EC point multiply with this.getPrivateKey() and ephemeral public key. This gives you a point M.
			ECPoint M = ephemeral.getPublicPoint().multiply(new BigInteger(1, getPrivateKey())).normalize();

			// 4. Use the X component of point M and calculate the SHA512 hash H.
			byte[] H = RadixHash.SHA512of(M.getXCoord().getEncoded()).toByteArray();

			// 5. The first 32 bytes of H are called key_e and the last 32 bytes are called key_m.
			byte[] key_e = Arrays.copyOfRange(H, 0, 32);
			byte[] key_m = Arrays.copyOfRange(H, 32, 64);

			// 6. Read encrypted data
			byte[] encrypted = new byte[inputStream.readInt()];
			inputStream.readFully(encrypted);

			// 6. Read MAC
			byte[] mac = new byte[32];
			inputStream.readFully(mac);

			// 7. Compare MAC with MAC'. If not equal, decryption will fail.
			byte[] pkMac = publicKey.calculateMAC(key_m, IV, ephemeral, encrypted);
			if (!Arrays.equals(mac, pkMac)) {
				throw new MacMismatchException(pkMac, mac);
			}

			// 8. Decrypt the cipher text with AES-256-CBC, using IV as initialization vector, key_e as decryption key
			//    and the cipher text as payload. The output is the padded input text.
			return publicKey.crypt(false, IV, encrypted, key_e);
		}
		catch (IOException e)
		{
			// TODO: change type of exception thrown
			throw new RuntimeException("Failed to decrypt", e);
		}
	}

	public String decryptToAscii(byte[] data) throws MacMismatchException {
		return new String(this.decrypt(data));
	}


	@Override
	public int hashCode() {
		return this.publicKey.hashCode();
	}

	@Override
	public boolean equals(Object o) {
		return this.publicKey.equals(((ECKeyPair)o).getPublicKey());
	}
}
