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

import com.radixdlt.crypto.exception.PrivateKeyException;
import com.radixdlt.crypto.exception.PublicKeyException;
import org.bouncycastle.math.ec.ECPoint;

/**
 * Interface for signature and public key computation functions.
 *
 * <p>The intent behind this interface is that the actual implementations can easily be replaced
 * when required.
 *
 * <p>Note that all methods must be thread safe.
 */
interface KeyHandler {

  /**
   * Sign the specified hash with the specified private key.
   *
   * @param hash The hash to sign
   * @param privateKey The private key to sign the hash with
   * @param enforceLowS If signature should enforce low values of signature part {@code S},
   *     according to <a
   *     href="https://github.com/bitcoin/bips/blob/master/bip-0062.mediawiki#Low_S_values_in_signatures">BIP-62</a>
   * @param useDeterministicSignatures If signing should use randomness or be deterministic
   *     according to <a href="https://tools.ietf.org/html/rfc6979">RFC6979</a>.
   * @return An {@link ECDSASignature} with {@code r} and {@code s} values included
   */
  ECDSASignature sign(
      byte[] hash,
      byte[] privateKey,
      byte[] publicKey,
      boolean enforceLowS,
      boolean useDeterministicSignatures);

  /**
   * Verify the specified signature against the specified hash with the specified public key.
   *
   * @param hash The hash to verify against
   * @param signature The signature to verify
   * @param publicKeyPoint The public key point to verify the signature with
   * @return An boolean indicating whether the signature could be successfully validated
   */
  boolean verify(byte[] hash, ECDSASignature signature, ECPoint publicKeyPoint);

  /**
   * Compute a public key for the specified private key.
   *
   * @param privateKey The private key to compute the public key for
   * @return A compressed public key
   * @throws PrivateKeyException If the {@code privateKey} is invalid
   * @throws PublicKeyException If computed {@code publicKey} is invalid
   */
  byte[] computePublicKey(byte[] privateKey) throws PrivateKeyException, PublicKeyException;

  /**
   * Sign the specified hash with the specified private by using randomness and enforced low {@code
   * S} values, see documentation of {@link #sign(byte[], byte[], byte[], boolean, boolean)} for
   * more details.
   *
   * @param hash The hash to sign
   * @param privateKey The private key to sign the hash with
   * @return An {@link ECDSASignature} with {@code r} and {@code s} values included
   */
  default ECDSASignature sign(byte[] hash, byte[] privateKey, byte[] publicKey) {
    return sign(hash, privateKey, publicKey, true, false);
  }
}
