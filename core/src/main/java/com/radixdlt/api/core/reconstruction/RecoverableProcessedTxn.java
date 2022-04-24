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

package com.radixdlt.api.core.reconstruction;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.radixdlt.atom.SubstateId;
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.constraintmachine.REInstruction;
import com.radixdlt.constraintmachine.REProcessedTxn;
import com.radixdlt.constraintmachine.SubstateSerialization;
import com.radixdlt.constraintmachine.UpSubstate;
import com.radixdlt.engine.parser.ParsedTxn;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.SerializerConstants;
import com.radixdlt.serialization.SerializerDummy;
import com.radixdlt.serialization.SerializerId2;
import com.radixdlt.utils.Pair;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.IntFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.bouncycastle.util.Arrays;

@SerializerId2("xtx")
public class RecoverableProcessedTxn {
  @JsonProperty(SerializerConstants.SERIALIZER_NAME)
  @DsonOutput(value = {DsonOutput.Output.API, DsonOutput.Output.WIRE, DsonOutput.Output.PERSIST})
  SerializerDummy serializer = SerializerDummy.DUMMY;

  // TODO: Change this to be a map
  @JsonProperty("s")
  @DsonOutput(DsonOutput.Output.ALL)
  private final Map<Integer, List<byte[]>> shutdownSubstates;

  @JsonCreator
  public RecoverableProcessedTxn(@JsonProperty("s") Map<Integer, List<byte[]>> shutdownSubstates) {
    this.shutdownSubstates = shutdownSubstates == null ? Map.of() : shutdownSubstates;
  }

  public static RecoverableProcessedTxn from(
      REProcessedTxn txn, SubstateSerialization serialization) {
    var parsedTxn = txn.getParsedTxn();

    var stateUpdateGroups =
        txn.getGroupedStateUpdates().stream()
            .flatMap(
                stateUpdates ->
                    stateUpdates.stream()
                        .filter(
                            u -> {
                              var microOp =
                                  parsedTxn
                                      .instructions()
                                      .get(u.getInstructionIndex())
                                      .getMicroOp();
                              return switch (microOp) {
                                case DOWN, DOWNINDEX, VDOWN, LVDOWN -> true;
                                default -> false;
                              };
                            })
                        .map(
                            u -> {
                              var microOp =
                                  parsedTxn
                                      .instructions()
                                      .get(u.getInstructionIndex())
                                      .getMicroOp();
                              var data =
                                  switch (microOp) {
                                    case DOWN -> serialization.serialize((Particle) u.getParsed());
                                    case DOWNINDEX -> Arrays.concatenate(
                                        u.getId().asBytes(),
                                        serialization.serialize((Particle) u.getParsed()));
                                    case VDOWN, LVDOWN -> new byte[] {u.typeByte()};
                                    default -> throw new IllegalStateException();
                                  };
                              return Pair.of(u.getInstructionIndex(), data);
                            }))
            .collect(
                Collectors.groupingBy(
                    Pair::getFirst, Collectors.mapping(Pair::getSecond, Collectors.toList())));
    return new RecoverableProcessedTxn(stateUpdateGroups);
  }

  private RecoverableSubstate recoverUp(UpSubstate upSubstate) {
    ByteBuffer substate = upSubstate.getSubstateBuffer();
    SubstateId substateId = upSubstate.getSubstateId();
    return new RecoverableSubstateShutdown(substate, substateId, true);
  }

  private RecoverableSubstate recoverDown(REInstruction instruction, int index) {
    var dataList = shutdownSubstates.get(index);
    if (dataList.size() != 1) {
      throw new IllegalStateException("Multiple substates found for down instruction");
    }
    var substate = ByteBuffer.wrap(dataList.get(0));
    SubstateId substateId = instruction.getData();
    return new RecoverableSubstateShutdown(substate, substateId, false);
  }

  private RecoverableSubstate recoverLocalDown(
      REInstruction instruction, IntFunction<UpSubstate> localUpSubstates) {
    SubstateId substateId = instruction.getData();
    var index =
        substateId.getIndex().orElseThrow(() -> new IllegalStateException("Could not find index"));
    var substate = localUpSubstates.apply(index).getSubstateBuffer();
    return new RecoverableSubstateShutdown(substate, substateId, false);
  }

  private RecoverableSubstate recoverVirtualDown(REInstruction instruction, int index) {
    var dataList = shutdownSubstates.get(index);
    if (dataList.size() != 1) {
      throw new IllegalStateException("Multiple substates found for virtual down instruction");
    }
    SubstateId substateId = instruction.getData();
    return new RecoverableSubstateVirtualShutdown(dataList.get(0)[0], substateId);
  }

  private Stream<RecoverableSubstate> recoverDownIndex(int index) {
    var substates = shutdownSubstates.get(index);
    if (substates == null) {
      return Stream.of();
    }
    return substates.stream()
        .map(
            data -> {
              var buf = ByteBuffer.wrap(data);
              var substateId = SubstateId.fromBuffer(buf);
              var substate =
                  ByteBuffer.wrap(data, SubstateId.BYTES, data.length - SubstateId.BYTES);
              return new RecoverableSubstateShutdown(substate, substateId, false);
            });
  }

  public List<List<RecoverableSubstate>> recoverStateUpdates(ParsedTxn parsedTxn) {
    var substateGroups = new ArrayList<List<RecoverableSubstate>>();
    var substateUpdates = new ArrayList<RecoverableSubstate>();
    var upSubstates = new ArrayList<UpSubstate>();

    for (int i = 0; i < parsedTxn.instructions().size(); i++) {
      var instruction = parsedTxn.instructions().get(i);
      if (!instruction.isStateUpdate()) {
        if (instruction.getMicroOp() == REInstruction.REMicroOp.END) {
          substateGroups.add(substateUpdates);
          substateUpdates = new ArrayList<>();
        }
        continue;
      }

      switch (instruction.getMicroOp()) {
        case UP -> {
          UpSubstate upSubstate = instruction.getData();
          substateUpdates.add(recoverUp(upSubstate));
          upSubstates.add(upSubstate);
        }
        case DOWN -> substateUpdates.add(recoverDown(instruction, i));
        case LDOWN -> substateUpdates.add(recoverLocalDown(instruction, upSubstates::get));
        case VDOWN, LVDOWN -> substateUpdates.add(recoverVirtualDown(instruction, i));
        case DOWNINDEX -> recoverDownIndex(i).forEach(substateUpdates::add);
        default -> {
          // ignored
        }
      }
    }

    return substateGroups;
  }
}
