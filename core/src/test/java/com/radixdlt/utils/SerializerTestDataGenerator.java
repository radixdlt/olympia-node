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

package com.radixdlt.utils;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.hash.HashCode;
import com.radixdlt.atom.Txn;
import com.radixdlt.consensus.BFTHeader;
import com.radixdlt.consensus.HighQC;
import com.radixdlt.consensus.LedgerHeader;
import com.radixdlt.consensus.Proposal;
import com.radixdlt.consensus.QuorumCertificate;
import com.radixdlt.consensus.TimeoutCertificate;
import com.radixdlt.consensus.TimestampedECDSASignature;
import com.radixdlt.consensus.TimestampedECDSASignatures;
import com.radixdlt.consensus.UnverifiedVertex;
import com.radixdlt.consensus.Vote;
import com.radixdlt.consensus.VoteData;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.BFTValidator;
import com.radixdlt.consensus.bft.BFTValidatorSet;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.crypto.ECDSASignature;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.ledger.AccumulatorState;
import java.math.BigInteger;
import java.util.List;
import java.util.Optional;
import java.util.Random;

public class SerializerTestDataGenerator {

  private static final Random random = new Random();

  private SerializerTestDataGenerator() {
    // no-op
  }

  public static QuorumCertificate randomQC() {
    return new QuorumCertificate(randomVoteData(), randomTimestampedECDSASignatures());
  }

  public static Vote randomVote() {
    return new Vote(
        BFTNode.random(),
        randomVoteData(),
        Math.abs(random.nextLong()) + 1,
        randomECDSASignature(),
        randomHighQC(),
        Optional.of(randomECDSASignature()));
  }

  public static Proposal randomProposal() {
    var qc = randomQC();
    var txn = Txn.create(new byte[] {0, 1, 2, 3});
    var author = BFTNode.create(ECKeyPair.generateNew().getPublicKey());
    var vertex = UnverifiedVertex.create(qc, randomView(), List.of(txn), author);
    return new Proposal(vertex, qc, ECDSASignature.zeroSignature(), Optional.empty());
  }

  public static VoteData randomVoteData() {
    return new VoteData(randomBFTHeader(), randomBFTHeader(), randomBFTHeader());
  }

  public static BFTHeader randomBFTHeader() {
    return new BFTHeader(
        randomView(),
        HashCode.fromLong(random.nextLong()),
        LedgerHeader.create(
            Math.abs(random.nextLong()),
            randomView(),
            new AccumulatorState(
                Math.abs(random.nextLong()) + 1, HashCode.fromLong(random.nextLong())),
            Math.abs(random.nextLong()) + 1,
            BFTValidatorSet.from(
                ImmutableSet.<BFTValidator>builder()
                    .add(BFTValidator.from(BFTNode.random(), UInt256.from(random.nextLong())))
                    .build())));
  }

  public static TimestampedECDSASignatures randomTimestampedECDSASignatures() {
    return new TimestampedECDSASignatures(
        ImmutableMap.<BFTNode, TimestampedECDSASignature>builder()
            .put(BFTNode.random(), randomTimestampedECDSASignature())
            .build());
  }

  public static TimestampedECDSASignature randomTimestampedECDSASignature() {
    return TimestampedECDSASignature.from(Math.abs(random.nextLong()), randomECDSASignature());
  }

  public static ECDSASignature randomECDSASignature() {
    return ECDSASignature.create(
        BigInteger.valueOf(Math.abs(random.nextLong())),
        BigInteger.valueOf(Math.abs(random.nextLong())),
        (random.nextInt() & 1));
  }

  public static HighQC randomHighQC() {
    return HighQC.from(randomQC(), randomQC(), Optional.of(randomTimeoutCertificate()));
  }

  public static TimeoutCertificate randomTimeoutCertificate() {
    return new TimeoutCertificate(
        Math.abs(random.nextLong()), randomView(), randomTimestampedECDSASignatures());
  }

  public static View randomView() {
    return View.of(Math.abs(random.nextLong()));
  }
}
