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

import com.radixdlt.crypto.exception.PrivateKeyException;
import com.radixdlt.crypto.exception.PublicKeyException;
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

import com.radixdlt.SecurityCritical;
import com.radixdlt.SecurityCritical.SecurityKind;

import java.math.BigInteger;
import java.security.KeyFactory;

@SecurityCritical({ SecurityKind.KEY_GENERATION, SecurityKind.SIG_SIGN, SecurityKind.SIG_VERIFY })
final class BouncyCastleKeyHandler implements KeyHandler {
	private final BigInteger curveOrder;
	private final BigInteger halfCurveOrder;
	private final ECDomainParameters domain;
	private final ECParameterSpec spec;

	BouncyCastleKeyHandler(X9ECParameters curve) {
		this.curveOrder = curve.getN();
		this.halfCurveOrder = curve.getN().shiftRight(1);
		this.domain = new ECDomainParameters(curve.getCurve(), curve.getG(), curve.getN(), curve.getH());
		this.spec = new ECParameterSpec(curve.getCurve(), curve.getG(), curve.getN(), curve.getH());
	}

	@Override
	public ECDSASignature sign(byte[] hash, byte[] privateKey, boolean enforceLowS, boolean useDeterministicSignatures) {
		final DSAKCalculator kCalculator = useDeterministicSignatures ? new HMacDSAKCalculator(new SHA256Digest()) : new RandomDSAKCalculator();
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
	public boolean verify(byte[] hash, ECDSASignature signature, byte[] publicKey) {
		ECDSASigner verifier = new ECDSASigner();
		verifier.init(false, new ECPublicKeyParameters(spec.getCurve().decodePoint(publicKey), domain));
		return verifier.verifySignature(hash, signature.getR(), signature.getS());
	}

	@Override
	public byte[] computePublicKey(byte[] privateKey) throws PrivateKeyException, PublicKeyException {
		ECKeyUtils.validatePrivate(privateKey);

		BigInteger d = new BigInteger(1, privateKey);
		try {
			ECPublicKeySpec publicKeySpec = new ECPublicKeySpec(spec.getG().multiply(d), spec);

			// Note that the provider here *must* be "BC" for this to work
			// correctly because we are using the bouncy castle ECPublicKeySpec,
			// and are casting to a bouncy castle ECPublicKey.
			return ((ECPublicKey) KeyFactory.getInstance("EC", "BC").generatePublic(publicKeySpec)).getQ().getEncoded(true);
		} catch (Exception e) {
			throw new PublicKeyException(e);
		}
	}
}
