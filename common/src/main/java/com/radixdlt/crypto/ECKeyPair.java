/* Copyright 2021 Radix Publishing Ltd incorporated in Jersey (Channel Islands).
 *
 * Licensed under the Radix License, Version 1.0 (the "License"); you may not use this
 * file except in compliance with the License. You may obtain a copy of the License at:
 *
 * radixfoundation.org/licenses/LICENSE-v1
 *
 * The Licensor hereby grants permission for the Canonical version of the Work to be
 * published, distributed and used under or by reference to the Licensor’s trademark
 * Radix ® and use of any unregistered trade names, logos or get-up.
 *
 * The Licensor provides the Work (and each Contributor provides its Contributions) on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied,
 * including, without limitation, any warranties or conditions of TITLE, NON-INFRINGEMENT,
 * MERCHANTABILITY, or FITNESS FOR A PARTICULAR PURPOSE.
 *
 * Whilst the Work is capable of being deployed, used and adopted (instantiated) to create
 * a distributed ledger it is your responsibility to test and validate the code, together
 * with all logic and performance of that code under all foreseeable scenarios.
 *
 * The Licensor does not make or purport to make and hereby excludes liability for all
 * and any representation, warranty or undertaking in any form whatsoever, whether express
 * or implied, to any entity or person, including any representation, warranty or
 * undertaking, as to the functionality security use, value or other characteristics of
 * any distributed ledger nor in respect the functioning or value of any tokens which may
 * be created stored or transferred using the Work. The Licensor does not warrant that the
 * Work or any use of the Work complies with any law or regulation in any territory where
 * it may be implemented or used or that it will be appropriate for any specific purpose.
 *
 * Neither the licensor nor any current or former employees, officers, directors, partners,
 * trustees, representatives, agents, advisors, contractors, or volunteers of the Licensor
 * shall be liable for any direct or indirect, special, incidental, consequential or other
 * losses of any kind, in tort, contract or otherwise (including but not limited to loss
 * of revenue, income or profits, or loss of use or data, or loss of reputation, or loss
 * of any economic or other opportunity of whatsoever nature or howsoever arising), arising
 * out of or in connection with (without limitation of any use, misuse, of any ledger system
 * or use made or its functionality or any performance or operation of any code or protocol
 * caused by bugs or programming or logic errors or otherwise);
 *
 * A. any offer, purchase, holding, use, sale, exchange or transmission of any
 * cryptographic keys, tokens or assets created, exchanged, stored or arising from any
 * interaction with the Work;
 *
 * B. any failure in a transmission or loss of any token or assets keys or other digital
 * artefacts due to errors in transmission;
 *
 * C. bugs, hacks, logic errors or faults in the Work or any communication;
 *
 * D. system software or apparatus including but not limited to losses caused by errors
 * in holding or transmitting tokens by any third-party;
 *
 * E. breaches or failure of security including hacker attacks, loss or disclosure of
 * password, loss of private key, unauthorised use or misuse of such passwords or keys;
 *
 * F. any losses including loss of anticipated savings or other benefits resulting from
 * use of the Work or any changes to the Work (however implemented).
 *
 * You are solely responsible for; testing, validating and evaluation of all operation
 * logic, functionality, security and appropriateness of using the Work for any commercial
 * or non-commercial purpose and for any reproduction or redistribution by You of the
 * Work. You assume all risks associated with Your use of the Work and the exercise of
 * permissions under this License.
 */

package com.radixdlt.crypto;

import com.radixdlt.SecurityCritical;
import com.radixdlt.SecurityCritical.SecurityKind;
import com.radixdlt.crypto.exception.PrivateKeyException;
import com.radixdlt.crypto.exception.PublicKeyException;
import com.radixdlt.identifiers.EUID;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.Objects;
import org.bouncycastle.crypto.AsymmetricCipherKeyPair;
import org.bouncycastle.crypto.generators.ECKeyPairGenerator;
import org.bouncycastle.crypto.params.ECKeyGenerationParameters;
import org.bouncycastle.crypto.params.ECPrivateKeyParameters;
import org.bouncycastle.crypto.params.ECPublicKeyParameters;
import org.bouncycastle.math.ec.ECPoint;

/** Asymmetric EC key pair provider fixed to curve 'secp256k1'. */
@SecurityCritical({
  SecurityKind.KEY_GENERATION,
  SecurityKind.SIG_SIGN,
  SecurityKind.PK_DECRYPT,
  SecurityKind.PK_ENCRYPT
})
public final class ECKeyPair implements Signing<ECDSASignature> {
  public static final int BYTES = 32;

  private final byte[] privateKey;
  private final ECPublicKey publicKey;

  private ECKeyPair(final byte[] privateKey, final ECPublicKey publicKey) {
    this.privateKey = privateKey;
    this.publicKey = publicKey;
  }

  /**
   * Generates a new private and public key pair based on randomness.
   *
   * @return a newly generated private key and it's corresponding {@link ECPublicKey}.
   */
  public static ECKeyPair generateNew() {
    try {
      ECKeyPairGenerator generator = new ECKeyPairGenerator();
      ECKeyGenerationParameters keygenParams =
          new ECKeyGenerationParameters(ECKeyUtils.domain(), ECKeyUtils.secureRandom());
      generator.init(keygenParams);
      AsymmetricCipherKeyPair keypair = generator.generateKeyPair();
      ECPrivateKeyParameters privParams = (ECPrivateKeyParameters) keypair.getPrivate();
      ECPublicKeyParameters pubParams = (ECPublicKeyParameters) keypair.getPublic();

      final ECPublicKey publicKey = ECPublicKey.fromEcPoint(pubParams.getQ());
      byte[] privateKeyBytes = ECKeyUtils.adjustArray(privParams.getD().toByteArray(), BYTES);
      ECKeyUtils.validatePrivate(privateKeyBytes);

      return new ECKeyPair(privateKeyBytes, publicKey);
    } catch (Exception e) {
      throw new IllegalStateException("Failed to generate ECKeyPair", e);
    }
  }

  /**
   * Generates a new, deterministic {@code ECKeyPair} instance by <b>hashing<b/> the provided seed.
   *
   * @param seed The seed to use when deriving the key pair instance, that is hashed (256 bits).
   * @return A key pair that corresponds to the hash of the provided seed.
   * @throws IllegalArgumentException if the seed is empty.
   */
  public static ECKeyPair fromSeed(byte[] seed) {
    Objects.requireNonNull(seed, "Seed must not be null");

    if (seed.length == 0) {
      throw new IllegalArgumentException("Seed must not be empty");
    }

    try {
      return fromPrivateKey(HashUtils.sha256(seed).asBytes());
    } catch (PrivateKeyException | PublicKeyException e) {
      throw new IllegalStateException("Should always be able to create private key from seed", e);
    }
  }

  /**
   * Restore {@link ECKeyPair} instance from given private key by computing corresponding public
   * key.
   *
   * @param privateKey byte array which contains private key.
   * @return A keypair for provided private key
   * @throws PrivateKeyException if input byte array does not represent a private key
   * @throws PublicKeyException if public key can't be computed for given private key
   */
  public static ECKeyPair fromPrivateKey(byte[] privateKey)
      throws PrivateKeyException, PublicKeyException {
    ECKeyUtils.validatePrivate(privateKey);

    return new ECKeyPair(
        privateKey, ECPublicKey.fromBytes(ECKeyUtils.keyHandler.computePublicKey(privateKey)));
  }

  public EUID euid() {
    return publicKey.euid();
  }

  public byte[] getPrivateKey() {
    return privateKey;
  }

  public ECPublicKey getPublicKey() {
    return publicKey;
  }
  // TODO move this to new class (yet to be created) `ECPrivateKey`.

  @Override
  public ECPoint multiply(ECPoint point) {
    BigInteger scalarFromPrivateKey = new BigInteger(1, privateKey);
    return point.multiply(scalarFromPrivateKey).normalize();
  }

  @Override
  public ECDSASignature sign(byte[] hash) {
    return ECKeyUtils.keyHandler.sign(hash, privateKey, publicKey.getBytes());
  }

  /**
   * Signs data using the ECPrivateKey resulting in an ECDSA signature.
   *
   * @param data The data to sign
   * @param enforceLowS If signature should enforce low values of signature part `S`, according to
   *     <a
   *     href="https://github.com/bitcoin/bips/blob/master/bip-0062.mediawiki#Low_S_values_in_signatures">BIP-62</a>
   * @param beDeterministic If signing should use randomness or be deterministic according to <a
   *     href="https://tools.ietf.org/html/rfc6979">RFC6979</a>.
   * @return An ECDSA Signature.
   */
  public ECDSASignature sign(byte[] data, boolean enforceLowS, boolean beDeterministic) {
    return ECKeyUtils.keyHandler.sign(
        data, privateKey, publicKey.getBytes(), enforceLowS, beDeterministic);
  }

  @Override
  public boolean equals(Object object) {
    if (this == object) {
      return true;
    }
    if (object instanceof ECKeyPair) {
      ECKeyPair other = (ECKeyPair) object;
      // Comparing private keys should be sufficient
      return Arrays.equals(other.getPrivateKey(), getPrivateKey());
    }
    return false;
  }

  @Override
  public int hashCode() {
    return Arrays.hashCode(getPrivateKey());
  }

  @Override
  public String toString() {
    // Not going to print the private key here
    return String.format("%s[%s]", getClass().getSimpleName(), getPublicKey().toHex());
  }
}
