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
import org.bouncycastle.asn1.x9.X9IntegerConverter;
import org.bouncycastle.crypto.ec.CustomNamedCurves;
import org.bouncycastle.crypto.params.ECDomainParameters;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.jce.spec.ECParameterSpec;
import org.bouncycastle.math.ec.ECAlgorithms;
import org.bouncycastle.math.ec.ECCurve;
import org.bouncycastle.math.ec.ECPoint;
import org.bouncycastle.math.ec.FixedPointUtil;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.primitives.UnsignedBytes;
import com.radixdlt.crypto.exception.PrivateKeyException;
import com.radixdlt.crypto.exception.PublicKeyException;
import com.radixdlt.utils.Bytes;
import com.radixdlt.utils.RuntimeUtils;
import com.radixdlt.utils.functional.Result;

import java.math.BigInteger;
import java.security.Provider;
import java.security.SecureRandom;
import java.security.Security;
import java.util.Arrays;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Utilities used by both {@link ECPublicKey} and {@link ECKeyPair}.
 */
public class ECKeyUtils {

	private ECKeyUtils() {
		throw new IllegalStateException("Can't construct");
	}

	private static final String CURVE_NAME = "secp256k1";
	private static SecureRandom secureRandom;
	private static X9ECParameters curve;
	private static ECDomainParameters domain;
	private static ECParameterSpec spec;
	private static byte[] order;

	public static SecureRandom secureRandom() {
		return secureRandom;
	}

	public static X9ECParameters curve() {
		return curve;
	}

	public static ECParameterSpec spec() {
		return spec;
	}

	public static ECDomainParameters domain() {
		return domain;
	}

	static {
		install();
	}

	static synchronized void install() {
		if (RuntimeUtils.isAndroidRuntime()) {
			// Reference class so static initialiser is called.
			LinuxSecureRandom.class.getName();
			// Ensure the library version of BouncyCastle is used for Android
			Security.removeProvider(BouncyCastleProvider.PROVIDER_NAME);
		}
		Provider requiredBouncyCastleProvider = new BouncyCastleProvider();
		Provider currentBouncyCastleProvider = Security.getProvider(BouncyCastleProvider.PROVIDER_NAME);

		// Check if the currently installed version of BouncyCastle is the version
		// we want. NOTE! That Android has a stripped down version of BouncyCastle
		// by default.
		if (isOfRequiredVersion(currentBouncyCastleProvider, requiredBouncyCastleProvider)) {
			Security.insertProviderAt(requiredBouncyCastleProvider, 1);
		}

		secureRandom = new SecureRandom();

		curve = CustomNamedCurves.getByName(CURVE_NAME);
		domain = new ECDomainParameters(curve.getCurve(), curve.getG(), curve.getN(), curve.getH());
		spec = new ECParameterSpec(curve.getCurve(), curve.getG(), curve.getN(), curve.getH());
		order = adjustArray(domain.getN().toByteArray(), ECKeyPair.BYTES);
		FixedPointUtil.precompute(curve.getG());
	}

	private static boolean isOfRequiredVersion(Provider currentBouncyCastleProvider, Provider requiredBouncyCastleProvider) {
		return currentBouncyCastleProvider == null
			|| !currentBouncyCastleProvider.getVersionStr().equals(requiredBouncyCastleProvider.getVersionStr());
	}

	// Must be after secureRandom init
	static final KeyHandler keyHandler = new BouncyCastleKeyHandler(curve);

	static void validatePrivate(byte[] privateKey) throws PrivateKeyException {
		if (privateKey == null) {
			throw new PrivateKeyException("Private key is null");
		}

		if (privateKey.length != ECKeyPair.BYTES) {
			throw new PrivateKeyException("Private key is invalid length: " + privateKey.length);
		}

		if (greaterOrEqualOrder(privateKey)) {
			throw new PrivateKeyException("Private key is greater than or equal to curve order");
		}

		int pklen = privateKey.length;
		if (allZero(privateKey, 0, pklen - 1)) {
			byte lastByte = privateKey[pklen - 1];
			if (lastByte == 0) {
				throw new PrivateKeyException("Private key is " + lastByte);
			}
		}
	}

	static void validatePublic(byte[] publicKey) throws PublicKeyException {
		if (publicKey == null || publicKey.length == 0) {
			throw new PublicKeyException("Public key is empty");
		}

		int pubkey0 = publicKey[0] & 0xFF;
		switch (pubkey0) {
			case 2:
			case 3:
				if (publicKey.length != ECPublicKey.COMPRESSED_BYTES) {
					throw new PublicKeyException("Public key has invalid compressed size");
				}
				break;
			case 4:
				if (publicKey.length != ECPublicKey.UNCOMPRESSED_BYTES) {
					throw new PublicKeyException("Public key has invalid uncompressed size");
				}
				break;
			default:
				throw new PublicKeyException("Public key has invalid format");
		}
	}

	private static final X9IntegerConverter CONVERTER = new X9IntegerConverter();

	private static ECCurve ecCurve() {
		return curve.getCurve();
	}

	private static ECPoint decompressKey(BigInteger xBN, boolean yBit) {
		byte[] compEnc = CONVERTER.integerToBytes(xBN, 1 + CONVERTER.getByteLength(ecCurve()));

		compEnc[0] = (byte) (yBit ? 0x03 : 0x02);

		try {
			return ecCurve().decodePoint(compEnc);
		} catch (IllegalArgumentException e) {
			// the compressed key was invalid
			return null;
		}
	}

	static int calculateV(BigInteger r, BigInteger s, byte[] publicKey, byte[] hash) {
		return tryV(0, r, s, publicKey, hash)
			.or(() -> tryV(1, r, s, publicKey, hash))
			.orElseThrow(() -> new IllegalStateException("Unable to calculate V byte for public key"));
	}

	private static Optional<Integer> tryV(int v, BigInteger r, BigInteger s, byte[] publicKey, byte[] hash) {
		return ECKeyUtils.recoverFromSignature(v, r, s, hash)
			.filter(q -> Arrays.equals(q.getEncoded(false), publicKey))
			.map(__ -> v);
	}

	/**
	 * Restore public key from recoverable signature.
	 *
	 * @param signature recoverable signature (with correct bit 'v')
	 * @param hash hash from which signature was created
	 *
	 * @return recovered public key
	 */
	static Optional<ECPoint> recoverFromSignature(ECDSASignature signature, byte[] hash) {
		return recoverFromSignature(signature.getV(), signature.getR(), signature.getS(), hash);
	}

	/**
	 * Restore recoverable signature from non-recoverable signature and public key.
	 *
	 * @param signature original non-recoverable signature
	 * @param hash hash from which signature was created
	 * @param publicKey corresponding public key from the private key used to sign hash.
	 *
	 * @return recoverable signature
	 */
	public static Result<ECDSASignature> toRecoverable(ECDSASignature signature, byte[] hash, ECPublicKey publicKey) {
		try {
			var v = calculateV(signature.getR(), signature.getS(), publicKey.getBytes(), hash);

			return Result.ok(ECDSASignature.create(signature.getR(), signature.getS(), v));
		} catch (IllegalStateException e) {
			return Result.fail(e);
		}
	}

	static Optional<ECPoint> recoverFromSignature(int v, BigInteger r, BigInteger s, byte[] hash) {
		var curveN = curve.getN();
		var decompressedPoint = decompressKey(r, (v & 1) == 1);

		if (decompressedPoint == null || !decompressedPoint.multiply(curveN).isInfinity()) {
			return Optional.empty();
		}

		var inverse = r.modInverse(curveN);
		var candidate = new BigInteger(1, hash);
		var negModCandidate = BigInteger.ZERO.subtract(candidate).mod(curveN);

		return Optional.of(
			ECAlgorithms.sumOfTwoMultiplies(
				curve.getG(),
				inverse.multiply(negModCandidate).mod(curveN),
				decompressedPoint,
				inverse.multiply(s).mod(curveN)
			))
			.filter(Predicate.not(ECPoint::isInfinity));
	}

	/**
	 * Adjusts the specified array so that is is equal to the specified length.
	 * <ul>
	 *   <li>
	 *     If the array is equal to the specified length, it is returned
	 *     without change.
	 *   </li>
	 *   <li>
	 *     If array is shorter than the specified length, a new array that
	 *     is zero padded at the front is returned.  The specified array is
	 *     filled with zeros to prevent information leakage.
	 *   </li>
	 *   <li>
	 *     If the array is longer than the specified length, a new array
	 *     with sufficient leading zeros removed is returned.  The specified
	 *     array is filled with zeros to prevent information leakage.
	 *     An {@code IllegalArgumentException} is thrown if the specified
	 *     array does not have sufficient leading zeros to allow it to be
	 *     truncated to the specified length.
	 *   </li>
	 * </ul>
	 *
	 * @param array The specified array
	 * @param length The specified length
	 *
	 * @return An array of the specified length as described above
	 *
	 * @throws IllegalArgumentException if the specified array is longer than
	 * 	the specified length, and does not have sufficient leading zeros
	 * 	to allow truncation to the specified length.
	 * @throws NullPointerException if the specified array is {@code null}
	 */
	static byte[] adjustArray(byte[] array, int length) {
		if (length == array.length) {
			// Length is fine
			return array;
		}
		final byte[] result;
		if (length > array.length) {
			// Needs zero padding at front
			result = new byte[length];
			System.arraycopy(array, 0, result, length - array.length, array.length);
		} else {
			// Must be longer, need to drop zeros at front -> error if dropped bytes are not zero
			int offset = 0;
			while (array.length - offset > length) {
				if (array[offset] != 0) {
					throw new IllegalArgumentException(String.format(
						"Array is greater than %s bytes: %s", length, Bytes.toHexString(array)
					));
				}
				offset += 1;
			}
			// Now copy length bytes from offset within array
			result = Arrays.copyOfRange(array, offset, offset + length);
		}
		// Zero out original array so as to avoid information leaks
		Arrays.fill(array, (byte) 0);
		return result;
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
