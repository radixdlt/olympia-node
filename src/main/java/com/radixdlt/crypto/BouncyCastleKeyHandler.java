/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 */

package com.radixdlt.crypto;

import org.bouncycastle.asn1.x9.X9ECParameters;
import org.bouncycastle.crypto.digests.SHA256Digest;
import org.bouncycastle.crypto.params.ECDomainParameters;
import org.bouncycastle.crypto.params.ECPrivateKeyParameters;
import org.bouncycastle.crypto.params.ECPublicKeyParameters;
import org.bouncycastle.crypto.signers.DSAKCalculator;
import org.bouncycastle.crypto.signers.ECDSASigner;
import org.bouncycastle.crypto.signers.HMacDSAKCalculator;
import org.bouncycastle.crypto.signers.RandomDSAKCalculator;
import org.bouncycastle.jce.interfaces.ECPublicKey;
import org.bouncycastle.jce.spec.ECParameterSpec;
import org.bouncycastle.jce.spec.ECPublicKeySpec;

import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.SecureRandom;
import java.util.Objects;

final class BouncyCastleKeyHandler implements KeyHandler {
	private final BigInteger curveOrder;
	private final BigInteger halfCurveOrder;
	private final X9ECParameters curve;
	private final ECDomainParameters domain;
	private final ECParameterSpec spec;
	private final SecureRandom secureRandom;

	BouncyCastleKeyHandler(SecureRandom secureRandom, X9ECParameters curve) {
		this.secureRandom = Objects.requireNonNull(secureRandom);
		this.curve = Objects.requireNonNull(curve);
		this.curveOrder = curve.getN();
		this.halfCurveOrder = curve.getN().shiftRight(1);
		this.domain = new ECDomainParameters(curve.getCurve(), curve.getG(), curve.getN(), curve.getH());
		this.spec = new ECParameterSpec(curve.getCurve(), curve.getG(), curve.getN(), curve.getH());
	}

	/**
	 * Signs data using the ECPrivateKey resulting in an ECDSA signature.
	 *
	 * @param hash The hashed data to sign
	 * @param enforceLowS If signature should enforce low values of signature part `S`, according to
	 * <a href="https://github.com/bitcoin/bips/blob/master/bip-0062.mediawiki#Low_S_values_in_signatures">BIP-62</a>
	 * @param beDeterministic If signing should use randomness or be deterministic according to
	 * <a href="https://tools.ietf.org/html/rfc6979">RFC6979</a>.
	 * @return An ECDSA Signature.
	 */
	@Override
	public ECDSASignature sign(byte[] hash, byte[] privateKey, boolean enforceLowS, boolean beDeterministic) throws CryptoException {
		final DSAKCalculator kCalculator = beDeterministic ? new HMacDSAKCalculator(new SHA256Digest()) : new RandomDSAKCalculator();
		ECDSASigner signer = new ECDSASigner(kCalculator);
		BigInteger privateKeyScalar = new BigInteger(1, privateKey);
		ECPrivateKeyParameters privateKeyParameters = new ECPrivateKeyParameters(privateKeyScalar, domain);
		signer.init(true, privateKeyParameters);

		BigInteger[] components = signer.generateSignature(hash);
		BigInteger r = components[0];
		BigInteger s = components[1];

		boolean sIsLow = s.compareTo(this.halfCurveOrder) <= 0;

		if (enforceLowS && !sIsLow) {
			s = this.curveOrder.subtract(s);
		}

		return new ECDSASignature(r, s);
	}

	@Override
	public boolean verify(byte[] hash, ECDSASignature signature, byte[] publicKey) throws CryptoException {
		ECDSASigner verifier = new ECDSASigner();
		verifier.init(false, new ECPublicKeyParameters(spec.getCurve().decodePoint(publicKey), domain));
		return verifier.verifySignature(hash, signature.getR(), signature.getS());
	}

	@Override
	public byte[] computePublicKey(byte[] privateKey) throws CryptoException {
		ECKeyUtils.validatePrivate(privateKey);

		BigInteger d = new BigInteger(1, privateKey);
		try {
			ECPublicKeySpec publicKeySpec = new ECPublicKeySpec(spec.getG().multiply(d), spec);

			// Note that the provider here *must* be "BC" for this to work
			// correctly because we are using the bouncy castle ECPublicKeySpec,
			// and are casting to a bouncy castle ECPublicKey.
			return ((ECPublicKey) KeyFactory.getInstance("EC", "BC").generatePublic(publicKeySpec)).getQ().getEncoded(true);
		} catch (Exception e) {
			throw new CryptoException(e);
		}
	}
}
