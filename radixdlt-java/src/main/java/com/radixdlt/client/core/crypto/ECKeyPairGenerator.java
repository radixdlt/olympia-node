package com.radixdlt.client.core.crypto;

import java.security.InvalidAlgorithmParameterException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SecureRandom;
import java.security.Security;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.radixdlt.client.core.util.AndroidUtil;
import org.bouncycastle.asn1.sec.SECNamedCurves;
import org.bouncycastle.asn1.x9.X9ECParameters;
import org.bouncycastle.crypto.ec.CustomNamedCurves;
import org.bouncycastle.crypto.params.ECDomainParameters;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.jce.spec.ECParameterSpec;

public final class ECKeyPairGenerator {
	private static final Map<Integer, ECDomainParameters> DOMAINS;

	static {
		if (AndroidUtil.isAndroidRuntime()) {
			Security.removeProvider(BouncyCastleProvider.PROVIDER_NAME);
		}
		Security.insertProviderAt(new BouncyCastleProvider(), 1);
		DOMAINS = Stream.of(256).collect(Collectors.toMap(Integer::new, (bits) -> {
			final X9ECParameters curve =
				bits == 256
					? CustomNamedCurves.getByName("secp" + bits + "k1")
					: SECNamedCurves.getByName("secp" + bits + "k1");
			return new ECDomainParameters(curve.getCurve(), curve.getG(), curve.getN(), curve.getH());
		}));
	}

	public static ECDomainParameters getDomain(int numBits) {
		int roundedNumBits = (((numBits - 1) / 32) + 1) * 32;
		return DOMAINS.get(roundedNumBits);
	}

	public static ECKeyPairGenerator newInstance() {
		return new ECKeyPairGenerator();
	}

	private final SecureRandom secureRandom = new SecureRandom();

	private ECKeyPairGenerator() {
	}

	public ECKeyPair generateKeyPair() {
		return generateKeyPair(256);
	}

	// Generates a new Public/Private Key pair
	public ECKeyPair generateKeyPair(int numBits) {
		try {
			KeyPairGenerator g2 = KeyPairGenerator.getInstance("EC", "BC");
			ECDomainParameters domain = getDomain(numBits);
			ECParameterSpec curveSpec = new ECParameterSpec(domain.getCurve(), domain.getG(),
				domain.getN(), domain.getH());
			g2.initialize(curveSpec, secureRandom);
			KeyPair keypair = g2.generateKeyPair();
			byte[] privateKey = ((org.bouncycastle.jce.interfaces.ECPrivateKey) keypair
				.getPrivate()).getD().toByteArray();

			if (privateKey.length != numBits / 8) {
				// Remove signed byte
				if (privateKey.length == (numBits / 8) + 1 && privateKey[0] == 0) {
					privateKey = Arrays.copyOfRange(privateKey, 1, privateKey.length);
				} else if (privateKey.length < numBits / 8) { // Pad
					byte[] copy = new byte[32];
					System.arraycopy(privateKey, 0, copy, 32 - privateKey.length, privateKey.length);
					privateKey = copy;
				} else {
					throw new RuntimeException();
				}
			}

			byte[] publicKey = ((org.bouncycastle.jce.interfaces.ECPublicKey) keypair.getPublic())
				.getQ().getEncoded(true);
			return new ECKeyPair(publicKey, privateKey);
		} catch (InvalidAlgorithmParameterException e) {
			throw new RuntimeException(e.getMessage());
		} catch (NoSuchAlgorithmException e) {
			throw new RuntimeException(e.getMessage());
		} catch (NoSuchProviderException e) {
			throw new RuntimeException(e.getMessage());
		}
	}

}
