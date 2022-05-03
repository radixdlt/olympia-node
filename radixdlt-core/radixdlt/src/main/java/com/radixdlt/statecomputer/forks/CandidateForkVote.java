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

package com.radixdlt.statecomputer.forks;

import com.google.common.hash.HashCode;
import com.google.common.primitives.Bytes;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.crypto.HashUtils;
import com.radixdlt.utils.Ints;
import com.radixdlt.utils.Longs;
import com.radixdlt.utils.Shorts;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.stream.Stream;

public record CandidateForkVote(HashCode payload) {
  /**
   * A constant nonce added to the fork vote. Each software version is supposed to have the same
   * nonce, but it is not taken into account when the system counts the votes.
   */
  public static final HashCode FORK_VOTE_NONCE =
      HashUtils.sha256("olympia".getBytes(StandardCharsets.US_ASCII));

  public static final int NAME_LEN = 16;
  public static final int CANDIDATE_FORK_HASH_LEN = 8;
  public static final int CANDIDATE_FORK_ID_LEN = NAME_LEN + CANDIDATE_FORK_HASH_LEN;
  public static final int NONCE_HASH_LEN = 8;
  public static final int TOTAL_LEN = CANDIDATE_FORK_ID_LEN + NONCE_HASH_LEN;

  public static CandidateForkVote create(ECPublicKey publicKey, CandidateForkConfig forkConfig) {
    final var payload = new byte[TOTAL_LEN];
    System.arraycopy(nameBytesWithPadding(forkConfig), 0, payload, 0, NAME_LEN);
    System.arraycopy(candidateForkHash(forkConfig), 0, payload, NAME_LEN, CANDIDATE_FORK_HASH_LEN);
    System.arraycopy(
        nonceHash(publicKey), 0, payload, NAME_LEN + CANDIDATE_FORK_HASH_LEN, NONCE_HASH_LEN);
    return new CandidateForkVote(HashCode.fromBytes(payload));
  }

  public String name() {
    return new String(nameBytes(), ForkConfig.FORK_NAME_CHARSET).trim();
  }

  private byte[] nameBytes() {
    return payloadSlice(0, NAME_LEN);
  }

  public byte[] candidateForkId() {
    return Bytes.concat(nameBytes(), candidateForkHash());
  }

  public byte[] candidateForkHash() {
    return payloadSlice(NAME_LEN, CANDIDATE_FORK_HASH_LEN);
  }

  public byte[] nonceHash() {
    return payloadSlice(NAME_LEN + CANDIDATE_FORK_HASH_LEN, NONCE_HASH_LEN);
  }

  private byte[] payloadSlice(int from, int len) {
    final var res = new byte[len];
    System.arraycopy(payload.asBytes(), from, res, 0, len);
    return res;
  }

  public static HashCode candidateForkId(CandidateForkConfig candidateForkConfig) {
    return HashCode.fromBytes(
        Bytes.concat(
            nameBytesWithPadding(candidateForkConfig), candidateForkHash(candidateForkConfig)));
  }

  private static byte[] nameBytesWithPadding(CandidateForkConfig candidateForkConfig) {
    final var nameBytesWithPadding = new byte[NAME_LEN];
    final var nameEncoded = candidateForkConfig.name().getBytes(ForkConfig.FORK_NAME_CHARSET);
    System.arraycopy(nameEncoded, 0, nameBytesWithPadding, 0, nameEncoded.length);
    return nameBytesWithPadding;
  }

  private static byte[] nonceHash(ECPublicKey publicKey) {
    return HashUtils.sha256(Bytes.concat(FORK_VOTE_NONCE.asBytes(), publicKey.getBytes()))
        .asBytes();
  }

  private static byte[] candidateForkHash(CandidateForkConfig candidateForkConfig) {
    final var nameBytes = candidateForkConfig.name().getBytes(ForkConfig.FORK_NAME_CHARSET);
    final var thresholdsBytes =
        candidateForkConfig.thresholds().stream()
            .flatMap(
                threshold ->
                    Stream.of(
                        Ints.toByteArray(threshold.numEpochsBeforeEnacted()),
                        Shorts.toByteArray(threshold.requiredStake())))
            .reduce(new byte[0], Bytes::concat);

    final var fullHash =
        HashUtils.sha256(
            Bytes.concat(
                nameBytes,
                thresholdsBytes,
                Longs.toByteArray(candidateForkConfig.minEpoch()),
                Longs.toByteArray(candidateForkConfig.maxEpoch())));
    return Arrays.copyOfRange(fullHash.asBytes(), 0, CANDIDATE_FORK_HASH_LEN);
  }
}
