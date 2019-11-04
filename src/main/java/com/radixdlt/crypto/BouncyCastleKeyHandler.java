package com.radixdlt.crypto;

import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.SecureRandom;
import java.util.Objects;
import org.bouncycastle.asn1.x9.X9ECParameters;
import org.bouncycastle.crypto.params.ECDomainParameters;
import org.bouncycastle.crypto.params.ECPrivateKeyParameters;
import org.bouncycastle.crypto.params.ECPublicKeyParameters;
import org.bouncycastle.crypto.params.ParametersWithRandom;
import org.bouncycastle.crypto.signers.ECDSASigner;
import org.bouncycastle.jce.interfaces.ECPublicKey;
import org.bouncycastle.jce.spec.ECParameterSpec;
import org.bouncycastle.jce.spec.ECPublicKeySpec;

class BouncyCastleKeyHandler implements KeyHandler {
	private final BigInteger halfCurveOrder;
	private final X9ECParameters curve;
	private final ECDomainParameters domain;
	private final ECParameterSpec spec;
	private final SecureRandom secureRandom;

	BouncyCastleKeyHandler(SecureRandom secureRandom, X9ECParameters curve) {
		this.secureRandom = Objects.requireNonNull(secureRandom);
		this.curve = Objects.requireNonNull(curve);
		this.halfCurveOrder = curve.getN().shiftRight(1);
		this.domain = new ECDomainParameters(curve.getCurve(), curve.getG(), curve.getN(), curve.getH());
		this.spec = new ECParameterSpec(curve.getCurve(), curve.getG(), curve.getN(), curve.getH());
	}

	@Override
	public ECSignature sign(byte[] hash, byte[] privateKey) throws CryptoException {
		ECDSASigner signer = new ECDSASigner();
		signer.init(true, new ParametersWithRandom(
			new ECPrivateKeyParameters(new BigInteger(1, privateKey), domain), secureRandom));
		BigInteger[] components = signer.generateSignature(hash);

		// Canonicalise the signature //
		return (components[1].compareTo(halfCurveOrder) > 0)
			? new ECSignature(components[0], curve.getN().subtract(components[1]))
			: new ECSignature(components[0], components[1]);
	}

	@Override
	public boolean verify(byte[] hash, ECSignature signature, byte[] publicKey) throws CryptoException {
		ECDSASigner verifier = new ECDSASigner();
		verifier.init(false, new ECPublicKeyParameters(spec.getCurve().decodePoint(publicKey), domain));
		return verifier.verifySignature(hash, signature.getR(), signature.getS());
	}

	@Override
	public byte[] computePublicKey(byte[] privateKey) throws CryptoException {
		BigInteger d = new BigInteger(1, privateKey);
		validatePrivate(d);

		try {
			ECPublicKeySpec publicKeySpec = new ECPublicKeySpec(spec.getG().multiply(d), spec);
			return ((ECPublicKey) KeyFactory.getInstance("EC").generatePublic(publicKeySpec)).getQ().getEncoded(true);
		} catch (Exception e) {
			throw new CryptoException(e);
		}
	}

	private void validatePrivate(BigInteger privateKey) throws CryptoException {
		if (privateKey == null) {
			throw new CryptoException("Private key is null");
		}

		if (privateKey.equals(BigInteger.ONE)) {
			throw new CryptoException("Private key is one");
		}

		int signum = privateKey.signum();
		if (signum == 0) {
			throw new CryptoException("Private key is zero");
		}

		if (signum < 0) {
			throw new CryptoException("Private key is negative");
		}
	}
}
