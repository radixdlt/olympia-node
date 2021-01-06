/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 */

package com.radixdlt.utils;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.hash.HashCode;
import com.radixdlt.consensus.BFTHeader;
import com.radixdlt.consensus.HighQC;
import com.radixdlt.consensus.LedgerHeader;
import com.radixdlt.consensus.QuorumCertificate;
import com.radixdlt.consensus.TimeoutCertificate;
import com.radixdlt.consensus.TimestampedECDSASignature;
import com.radixdlt.consensus.TimestampedECDSASignatures;
import com.radixdlt.consensus.TimestampedVoteData;
import com.radixdlt.consensus.Vote;
import com.radixdlt.consensus.VoteData;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.BFTValidator;
import com.radixdlt.consensus.bft.BFTValidatorSet;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.crypto.ECDSASignature;
import com.radixdlt.ledger.AccumulatorState;

import java.math.BigInteger;
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
            new TimestampedVoteData(randomVoteData(), random.nextLong()),
            randomECDSASignature(),
            randomHighQC(),
            Optional.of(randomECDSASignature())
        );
    }

    public static VoteData randomVoteData() {
        return new VoteData(randomBFTHeader(), randomBFTHeader(), randomBFTHeader());
    }

    public static BFTHeader randomBFTHeader() {
        return new BFTHeader(
            randomView(),
            HashCode.fromLong(random.nextLong()),
            LedgerHeader.create(
                random.nextLong(),
                randomView(),
                new AccumulatorState(
                    random.nextLong(), HashCode.fromLong(random.nextLong())
                ),
                random.nextLong(),
                BFTValidatorSet.from(
                    ImmutableSet.<BFTValidator>builder()
                        .add(BFTValidator.from(BFTNode.random(), UInt256.from(random.nextLong())))
                        .build())
            )
        );
    }

    public static TimestampedECDSASignatures randomTimestampedECDSASignatures() {
        return new TimestampedECDSASignatures(
            ImmutableMap.<BFTNode, TimestampedECDSASignature>builder()
                .put(BFTNode.random(), randomTimestampedECDSASignature())
                .build()
        );
    }

    public static TimestampedECDSASignature randomTimestampedECDSASignature() {
        return TimestampedECDSASignature.from(
            Math.abs(random.nextLong()),
            UInt256.from(random.nextLong()),
            randomECDSASignature());
    }

    public static ECDSASignature randomECDSASignature() {
        return new ECDSASignature(
            BigInteger.valueOf(Math.abs(random.nextLong())),
            BigInteger.valueOf(Math.abs(random.nextLong()))
        );
    }

    public static HighQC randomHighQC() {
        return HighQC.from(randomQC(), randomQC(), Optional.of(randomTimeoutCertificate()));
    }

    public static TimeoutCertificate randomTimeoutCertificate() {
        return new TimeoutCertificate(Math.abs(random.nextLong()), randomView(), randomTimestampedECDSASignatures());
    }

    public static View randomView() {
        return View.of(Math.abs(random.nextLong()));
    }
}
