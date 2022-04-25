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

package com.radixdlt.shell;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.radixdlt.atom.Txn;
import com.radixdlt.consensus.LedgerProof;
import com.radixdlt.environment.EventDispatcher;
import com.radixdlt.ledger.DtoLedgerProof;
import com.radixdlt.ledger.VerifiedTxnsAndProof;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.Serialization;
import com.radixdlt.serialization.SerializerConstants;
import com.radixdlt.serialization.SerializerDummy;
import com.radixdlt.serialization.SerializerId2;
import com.radixdlt.sync.CommittedReader;
import com.radixdlt.utils.Compress;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Objects;
import javax.annotation.concurrent.Immutable;

/** Utility class to write/restore ledger sync data from a file. */
public final class LedgerFileSync {

  /** Writes node's ledger sync data to a file. */
  public static void writeToFile(
      String fileName, Serialization serialization, CommittedReader committedReader)
      throws IOException {
    final var initialProof = committedReader.getEpochProof(1L);
    final var endProofOpt = committedReader.getLastProof();
    if (initialProof.isPresent() && endProofOpt.isPresent()) {
      final var endProof = endProofOpt.get();
      try (var out = new FileOutputStream(fileName)) {
        var currentProof = initialProof.get();
        var nextCommands = committedReader.getNextCommittedTxns(currentProof.toDto());
        while (nextCommands != null
            && nextCommands.getProof().getStateVersion() <= endProof.getStateVersion()) {
          final var commandsAndProof =
              new CommandsAndProof(nextCommands.getTxns(), nextCommands.getProof().toDto());
          final var serialized =
              Compress.compress(serialization.toDson(commandsAndProof, DsonOutput.Output.WIRE));
          out.write(ByteBuffer.allocate(4).putInt(serialized.length).array());
          out.write(serialized);
          currentProof = nextCommands.getProof();
          nextCommands = committedReader.getNextCommittedTxns(currentProof.toDto());
        }
      }
    }
  }

  /** Reads and processes ledger sync data from a file. */
  public static void restoreFromFile(
      String fileName,
      Serialization serialization,
      EventDispatcher<VerifiedTxnsAndProof> verifiedTxnsAndProofDispatcher)
      throws IOException {
    try (var in = new FileInputStream(fileName)) {
      while (in.available() > 0) {
        final var len = ByteBuffer.wrap(in.readNBytes(4)).getInt();
        final var data = in.readNBytes(len);
        final var wrapper =
            serialization.fromDson(Compress.uncompress(data), CommandsAndProof.class);
        final var proof = wrapper.getProof();
        // TODO: verify the proof
        final var verifiedTxnsAndProof =
            VerifiedTxnsAndProof.create(
                wrapper.getTxns(),
                new LedgerProof(proof.getOpaque(), proof.getLedgerHeader(), proof.getSignatures()));
        verifiedTxnsAndProofDispatcher.dispatch(verifiedTxnsAndProof);
      }
    }
  }

  @Immutable
  @SerializerId2("ledger_file_sync.commands_and_proof")
  private static final class CommandsAndProof {
    @JsonProperty(SerializerConstants.SERIALIZER_NAME)
    @DsonOutput(value = {DsonOutput.Output.API, DsonOutput.Output.WIRE, DsonOutput.Output.PERSIST})
    SerializerDummy serializer = SerializerDummy.DUMMY;

    @JsonProperty("txns")
    @DsonOutput(DsonOutput.Output.ALL)
    private final List<Txn> txns;

    @JsonProperty("proof")
    @DsonOutput(DsonOutput.Output.ALL)
    private final DtoLedgerProof proof;

    @JsonCreator
    public CommandsAndProof(
        @JsonProperty("txns") List<Txn> txns, @JsonProperty("proof") DtoLedgerProof proof) {
      this.txns = Objects.requireNonNull(txns);
      this.proof = Objects.requireNonNull(proof);
    }

    public List<Txn> getTxns() {
      return txns;
    }

    public DtoLedgerProof getProof() {
      return proof;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      final var that = (CommandsAndProof) o;
      return Objects.equals(txns, that.txns) && Objects.equals(proof, that.proof);
    }

    @Override
    public int hashCode() {
      return Objects.hash(txns, proof);
    }
  }
}
