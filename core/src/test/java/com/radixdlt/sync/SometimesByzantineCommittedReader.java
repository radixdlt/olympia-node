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

package com.radixdlt.sync;

import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import com.radixdlt.atom.Txn;
import com.radixdlt.consensus.LedgerHeader;
import com.radixdlt.consensus.LedgerProof;
import com.radixdlt.consensus.TimestampedECDSASignatures;
import com.radixdlt.crypto.Hasher;
import com.radixdlt.environment.EventProcessor;
import com.radixdlt.ledger.AccumulatorState;
import com.radixdlt.ledger.DtoLedgerProof;
import com.radixdlt.ledger.LedgerAccumulator;
import com.radixdlt.ledger.LedgerUpdate;
import com.radixdlt.ledger.VerifiedTxnsAndProof;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.function.UnaryOperator;
import org.junit.Ignore;

/** A reader which sometimes returns erroneous commands. */
public final class SometimesByzantineCommittedReader implements CommittedReader {
  private final InMemoryCommittedReader correctReader;
  private final LedgerAccumulator accumulator;
  private final Hasher hasher;
  private ReadType currentReadType;

  @Inject
  public SometimesByzantineCommittedReader(
      Random random,
      LedgerAccumulator accumulator,
      InMemoryCommittedReader correctReader,
      Hasher hasher) {
    this.correctReader = Objects.requireNonNull(correctReader);
    this.accumulator = Objects.requireNonNull(accumulator);
    this.currentReadType = ReadType.values()[random.nextInt(ReadType.values().length)];
    this.hasher = hasher;
  }

  public EventProcessor<LedgerUpdate> ledgerUpdateEventProcessor() {
    return this.correctReader.updateProcessor();
  }

  private static class ByzantineVerifiedCommandsAndProofBuilder {
    private DtoLedgerProof request;
    private UnaryOperator<Txn> commandMapper;
    private VerifiedTxnsAndProof base;
    private TimestampedECDSASignatures overwriteSignatures;
    private LedgerAccumulator accumulator;
    private Hasher hasher;

    public ByzantineVerifiedCommandsAndProofBuilder hasher(Hasher hasher) {
      this.hasher = hasher;
      return this;
    }

    public ByzantineVerifiedCommandsAndProofBuilder accumulator(
        DtoLedgerProof request, LedgerAccumulator accumulator) {
      this.request = request;
      this.accumulator = accumulator;
      return this;
    }

    public ByzantineVerifiedCommandsAndProofBuilder base(VerifiedTxnsAndProof base) {
      this.base = base;
      return this;
    }

    public ByzantineVerifiedCommandsAndProofBuilder replaceCommands(
        UnaryOperator<Txn> commandMapper) {
      this.commandMapper = commandMapper;
      return this;
    }

    public ByzantineVerifiedCommandsAndProofBuilder overwriteSignatures(
        TimestampedECDSASignatures overwriteSignatures) {
      this.overwriteSignatures = overwriteSignatures;
      return this;
    }

    public VerifiedTxnsAndProof build() {
      List<Txn> txns;
      if (commandMapper != null) {
        txns = base.getTxns().stream().map(commandMapper).collect(ImmutableList.toImmutableList());
      } else {
        txns = base.getTxns();
      }

      AccumulatorState accumulatorState;
      if (accumulator != null) {
        accumulatorState = request.getLedgerHeader().getAccumulatorState();
        for (var txn : txns) {
          accumulatorState = accumulator.accumulate(accumulatorState, txn.getId().asHashCode());
        }
      } else {
        accumulatorState = base.getProof().getAccumulatorState();
      }

      LedgerHeader ledgerHeader =
          LedgerHeader.create(
              base.getProof().getEpoch(),
              base.getProof().getView(),
              accumulatorState,
              base.getProof().timestamp(),
              base.getProof().getNextValidatorSet().orElse(null));
      var signatures =
          overwriteSignatures != null ? overwriteSignatures : base.getProof().getSignatures();
      var headerAndProof =
          new LedgerProof(base.getProof().toDto().getOpaque(), ledgerHeader, signatures);

      return VerifiedTxnsAndProof.create(txns, headerAndProof);
    }
  }

  @Ignore("This is not a test, but JUnit4 picks it as a test for some reason")
  private enum ReadType {
    GOOD {
      @Override
      VerifiedTxnsAndProof transform(
          DtoLedgerProof request,
          VerifiedTxnsAndProof correctCommands,
          LedgerAccumulator ledgerAccumulator,
          Hasher hasher) {
        return correctCommands;
      }
    },
    BAD_COMMANDS {
      @Override
      VerifiedTxnsAndProof transform(
          DtoLedgerProof request,
          VerifiedTxnsAndProof correctCommands,
          LedgerAccumulator ledgerAccumulator,
          Hasher hasher) {
        return new ByzantineVerifiedCommandsAndProofBuilder()
            .hasher(hasher)
            .base(correctCommands)
            .replaceCommands(cmd -> Txn.create(new byte[] {0}))
            .build();
      }
    },
    NO_SIGNATURES {
      @Override
      VerifiedTxnsAndProof transform(
          DtoLedgerProof request,
          VerifiedTxnsAndProof correctCommands,
          LedgerAccumulator accumulator,
          Hasher hasher) {
        return new ByzantineVerifiedCommandsAndProofBuilder()
            .hasher(hasher)
            .base(correctCommands)
            .replaceCommands(cmd -> Txn.create(new byte[] {0}))
            .accumulator(request, accumulator)
            .overwriteSignatures(new TimestampedECDSASignatures())
            .build();
      }
    },
    BAD_SIGNATURES {
      @Override
      VerifiedTxnsAndProof transform(
          DtoLedgerProof request,
          VerifiedTxnsAndProof correctCommands,
          LedgerAccumulator accumulator,
          Hasher hasher) {
        return new ByzantineVerifiedCommandsAndProofBuilder()
            .hasher(hasher)
            .base(correctCommands)
            .replaceCommands(cmd -> Txn.create(new byte[] {0}))
            .accumulator(request, accumulator)
            .build();
      }
    };

    abstract VerifiedTxnsAndProof transform(
        DtoLedgerProof request,
        VerifiedTxnsAndProof correctCommands,
        LedgerAccumulator ledgerAccumulator,
        Hasher hasher);
  }

  @Override
  public VerifiedTxnsAndProof getNextCommittedTxns(DtoLedgerProof start) {
    VerifiedTxnsAndProof correctResult = correctReader.getNextCommittedTxns(start);
    // TODO: Make epoch sync byzantine as well
    if (start.getLedgerHeader().isEndOfEpoch()) {
      return correctResult;
    }

    if (correctResult != null) {
      currentReadType =
          ReadType.values()[(currentReadType.ordinal() + 1) % ReadType.values().length];
      return currentReadType.transform(start, correctResult, accumulator, hasher);
    }

    return null;
  }

  @Override
  public Optional<LedgerProof> getEpochProof(long epoch) {
    return correctReader.getEpochProof(epoch);
  }

  @Override
  public Optional<LedgerProof> getLastProof() {
    return correctReader.getLastProof();
  }
}
