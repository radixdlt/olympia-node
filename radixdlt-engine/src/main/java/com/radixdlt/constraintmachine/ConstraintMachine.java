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

package com.radixdlt.constraintmachine;

import com.radixdlt.application.system.state.VirtualParent;
import com.radixdlt.application.tokens.state.TokenResource;
import com.radixdlt.atom.CloseableCursor;
import com.radixdlt.atom.Substate;
import com.radixdlt.atom.SubstateId;
import com.radixdlt.constraintmachine.exceptions.*;
import com.radixdlt.constraintmachine.meter.Meter;
import com.radixdlt.engine.parser.exceptions.TrailingBytesException;
import com.radixdlt.engine.parser.exceptions.TxnParseException;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.serialization.DeserializeException;
import com.radixdlt.store.CMStore;
import com.radixdlt.utils.Pair;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.function.Supplier;

/** An implementation of a UTXO based constraint machine which uses Radix's atom structure. */
// FIXME: unchecked, rawtypes
@SuppressWarnings({"rawtypes"})
public final class ConstraintMachine {
  private final Procedures procedures;
  private final VirtualSubstateDeserialization virtualSubstateDeserialization;
  private final SubstateDeserialization deserialization;
  private final Meter meter;

  public ConstraintMachine(
      Procedures procedures,
      SubstateDeserialization deserialization,
      VirtualSubstateDeserialization virtualSubstateDeserialization) {
    this(procedures, deserialization, virtualSubstateDeserialization, Meter.EMPTY);
  }

  public ConstraintMachine(ConstraintMachineConfig config) {
    this(
        config.procedures(),
        config.deserialization(),
        config.virtualSubstateDeserialization(),
        config.metering());
  }

  public ConstraintMachine(
      Procedures procedures,
      SubstateDeserialization deserialization,
      VirtualSubstateDeserialization virtualSubstateDeserialization,
      Meter meter) {
    this.procedures = Objects.requireNonNull(procedures);
    this.deserialization = deserialization;
    this.virtualSubstateDeserialization = virtualSubstateDeserialization;
    this.meter = Objects.requireNonNull(meter);
  }

  public SubstateDeserialization deserialization() {
    return deserialization;
  }

  public VirtualSubstateDeserialization virtualDeserialization() {
    return virtualSubstateDeserialization;
  }

  private static final class CMValidationState {
    private final Map<REAddr, TokenResource> localResources = new HashMap<>();
    private final Map<Integer, Pair<Substate, Supplier<ByteBuffer>>> localUpParticles =
        new HashMap<>();
    private final Set<SubstateId> remoteDownParticles = new HashSet<>();
    private final CMStore store;
    private final SubstateDeserialization deserialization;
    private final VirtualSubstateDeserialization virtualSubstateDeserialization;
    private int bootupCount = 0;

    CMValidationState(
        VirtualSubstateDeserialization virtualSubstateDeserialization,
        SubstateDeserialization deserialization,
        CMStore store) {
      this.deserialization = deserialization;
      this.virtualSubstateDeserialization = virtualSubstateDeserialization;
      this.store = store;
    }

    public Resources resources() {
      return addr -> {
        var local = localResources.get(addr);
        if (local != null) {
          return local;
        }

        return store
            .loadResource(addr)
            .map(deserialization::deserialize)
            .filter(TokenResource.class::isInstance)
            .map(TokenResource.class::cast)
            .orElseThrow(() -> new NotAResourceException(addr));
      };
    }

    public Optional<Particle> loadUpParticle(SubstateId substateId) {
      if (remoteDownParticles.contains(substateId)) {
        return Optional.empty();
      }

      return store.loadSubstate(substateId).map(deserialization::deserialize);
    }

    public void bootUp(Substate substate, Supplier<ByteBuffer> buffer) {
      localUpParticles.put(bootupCount, Pair.of(substate, buffer));

      if (substate.particle() instanceof TokenResource resource) {
        localResources.put(resource.addr(), resource);
      }
      bootupCount++;
    }

    public Particle virtualRead(SubstateId substateId)
        throws VirtualSubstateAlreadyDownException, VirtualParentStateDoesNotExist,
            DeserializeException {
      if (remoteDownParticles.contains(substateId)) {
        throw new VirtualSubstateAlreadyDownException(substateId);
      }

      var parentBuf = store.verifyVirtualSubstate(substateId);
      var parent = (VirtualParent) deserialization.deserialize(parentBuf);
      var typeByte = parent.data()[0];
      var keyBuf = substateId.getVirtualKey().orElseThrow();
      return virtualSubstateDeserialization.keyToSubstate(typeByte, keyBuf);
    }

    public Particle virtualShutdown(SubstateId substateId)
        throws VirtualSubstateAlreadyDownException, VirtualParentStateDoesNotExist,
            DeserializeException {
      var p = virtualRead(substateId);
      remoteDownParticles.add(substateId);
      return p;
    }

    public Particle localVirtualRead(SubstateId substateId)
        throws VirtualSubstateAlreadyDownException, VirtualParentStateDoesNotExist,
            DeserializeException {
      if (remoteDownParticles.contains(substateId)) {
        throw new VirtualSubstateAlreadyDownException(substateId);
      }

      var parentId = substateId.getVirtualParent().orElseThrow();
      var substate = localUpParticles.get(parentId.getIndex().orElseThrow());
      if (substate == null || !(substate.getFirst().particle() instanceof VirtualParent)) {
        throw new VirtualParentStateDoesNotExist(parentId);
      }

      var parent = (VirtualParent) substate.getFirst().particle();
      var typeByte = parent.data()[0];
      var keyBuf = substateId.getVirtualKey().orElseThrow();
      return virtualSubstateDeserialization.keyToSubstate(typeByte, keyBuf);
    }

    public Particle localVirtualShutdown(SubstateId substateId)
        throws VirtualSubstateAlreadyDownException, VirtualParentStateDoesNotExist,
            DeserializeException {
      var p = localVirtualRead(substateId);
      remoteDownParticles.add(substateId);
      return p;
    }

    public Particle localShutdown(int index) throws LocalSubstateNotFoundException {
      var substate = localUpParticles.remove(index);
      if (substate == null) {
        throw new LocalSubstateNotFoundException(index);
      }

      return substate.getFirst().particle();
    }

    public Particle localRead(int index) throws LocalSubstateNotFoundException {
      var substate = localUpParticles.get(index);
      if (substate == null) {
        throw new LocalSubstateNotFoundException(index);
      }

      return substate.getFirst().particle();
    }

    public Particle read(SubstateId substateId) throws SubstateNotFoundException {
      var read = loadUpParticle(substateId);
      if (read.isEmpty()) {
        throw new SubstateNotFoundException(substateId);
      }
      return read.get();
    }

    public Particle shutdown(SubstateId substateId) throws SubstateNotFoundException {
      var substate = read(substateId);
      remoteDownParticles.add(substateId);
      return substate;
    }

    @SuppressWarnings("resource")
    public CloseableCursor<Substate> getIndexedCursor(SubstateIndex index) {
      return CloseableCursor.wrapIterator(
              localUpParticles.values().stream()
                  .filter(s -> index.test(s.getSecond().get()))
                  .map(Pair::getFirst)
                  .iterator())
          .concat(
              () ->
                  store
                      .openIndexedCursor(index)
                      .map(this::substateFromRawData)
                      .filter(this::notInRemoteDownParticles));
    }

    private boolean notInRemoteDownParticles(Substate s) {
      return !remoteDownParticles.contains(s.substateId());
    }

    private Substate substateFromRawData(RawSubstateBytes r) {
      return new Substate(deserialization.deserialize(r.data()), r.asSubstateId());
    }
  }

  private Procedure loadProcedure(ReducerState reducerState, OpSignature opSignature)
      throws MissingProcedureException {
    var reducerStateClass = reducerState != null ? reducerState.getClass() : VoidReducerState.class;
    var key = ProcedureKey.of(reducerStateClass, opSignature);
    return this.procedures.getProcedure(key);
  }

  /**
   * Executes a transition procedure given the next spun particle and a current validation state.
   */
  private ReducerState callProcedure(
      Procedure procedure,
      Object procedureParam,
      ReducerState reducerState,
      Resources immutableAddrs,
      ExecutionContext context)
      throws SignedSystemException, InvalidPermissionException, AuthorizationException,
          MeterException, ProcedureException {
    // System permissions don't require additional authorization
    var authorization = procedure.authorization(procedureParam);
    var requiredLevel = authorization.permissionLevel();
    context.verifyPermissionLevel(requiredLevel);
    if (context.permissionLevel() != PermissionLevel.SYSTEM) {
      try {
        if (requiredLevel == PermissionLevel.USER) {
          this.meter.onUserProcedure(procedure.key(), procedureParam, context);
        } else if (requiredLevel == PermissionLevel.SUPER_USER) {
          this.meter.onSuperUserProcedure(procedure.key(), procedureParam, context);
        }
      } catch (Exception e) {
        throw new MeterException(e);
      }

      if (!context.skipAuthorization()) {
        try {
          authorization.authorizer().verify(immutableAddrs, context);
        } catch (Exception e) {
          throw new AuthorizationException(e);
        }
      }
    }

    return procedure.call(procedureParam, reducerState, immutableAddrs, context).state();
  }

  private static class MissingExpectedEndException extends Exception {}

  /**
   * Executes transition procedures and witness validators in a particle group and validates that
   * the particle group is well-formed.
   */
  List<List<REStateUpdate>> statefulVerify(
      ExecutionContext context, CMValidationState validationState, List<REInstruction> instructions)
      throws ConstraintMachineException {
    int instIndex = 0;
    var expectEnd = false;
    ReducerState reducerState = null;
    var readableAddrs = validationState.resources();
    var groupedStateUpdates = new ArrayList<List<REStateUpdate>>();
    var stateUpdates = new ArrayList<REStateUpdate>();

    meter.onStart(context);

    for (REInstruction inst : instructions) {
      try {
        if (expectEnd && inst.microOp() != REInstruction.REMicroOp.END) {
          throw new MissingExpectedEndException();
        }

        if (inst.microOp() == REInstruction.REMicroOp.SYSCALL) {
          CallData callData = inst.data();
          var opSignature = OpSignature.ofMethod(inst.microOp().getOp(), REAddr.ofSystem());
          var methodProcedure = loadProcedure(reducerState, opSignature);
          reducerState =
              callProcedure(methodProcedure, callData, reducerState, readableAddrs, context);
        } else if (inst.microOp().getOp() == REOp.READ) {
          final Particle nextParticle;
          if (inst.microOp() == REInstruction.REMicroOp.VREAD) {
            SubstateId substateId = inst.data();
            nextParticle = validationState.virtualRead(substateId);
          } else if (inst.microOp() == REInstruction.REMicroOp.READ) {
            SubstateId substateId = inst.data();
            nextParticle = validationState.read(substateId);
          } else if (inst.microOp() == REInstruction.REMicroOp.LREAD) {
            SubstateId substateId = inst.data();
            nextParticle = validationState.localRead(substateId.getIndex().orElseThrow());
          } else if (inst.microOp() == REInstruction.REMicroOp.LVREAD) {
            SubstateId substateId = inst.data();
            nextParticle = validationState.localVirtualRead(substateId);
          } else {
            throw new IllegalStateException("Unknown read op " + inst.microOp());
          }
          var eventId =
              OpSignature.ofSubstateUpdate(inst.microOp().getOp(), nextParticle.getClass());
          var methodProcedure = loadProcedure(reducerState, eventId);
          reducerState =
              callProcedure(methodProcedure, nextParticle, reducerState, readableAddrs, context);
          expectEnd = reducerState == null;
        } else if (inst.microOp().getOp() == REOp.DOWNINDEX
            || inst.microOp().getOp() == REOp.READINDEX) {
          byte[] raw = inst.data();
          var index =
              SubstateIndex.<Particle>create(
                  raw, validationState.deserialization.byteToClass(raw[0]));
          var substateCursor = validationState.getIndexedCursor(index);
          var tmp = stateUpdates;
          final int tmpInstIndex = instIndex;
          var iterator =
              new Iterator<Particle>() {
                @Override
                public boolean hasNext() {
                  return substateCursor.hasNext();
                }

                @Override
                public Particle next() {
                  // FIXME: this is a hack
                  // FIXME: do this via shutdownAll state update rather than individually
                  var substate = substateCursor.next();
                  if (inst.microOp().getOp() == REOp.DOWNINDEX) {
                    var typeByte = deserialization.classToByte(substate.particle().getClass());
                    tmp.add(
                        REStateUpdate.of(
                            REOp.DOWN,
                            tmpInstIndex,
                            substate.substateId(),
                            typeByte,
                            substate.particle(),
                            null));
                  }
                  return substate.particle();
                }
              };
          var substateIterator = new IndexedSubstateIterator<>(index, iterator);
          try {
            var eventId =
                OpSignature.ofSubstateUpdate(inst.microOp().getOp(), index.substateClass());
            var methodProcedure = loadProcedure(reducerState, eventId);
            reducerState =
                callProcedure(
                    methodProcedure, substateIterator, reducerState, readableAddrs, context);
          } finally {
            substateCursor.close();
          }
        } else if (inst.isStateUpdate()) {
          final SubstateId substateId;
          final Particle nextParticle;
          final Supplier<ByteBuffer> substateBuffer;
          if (inst.microOp() == REInstruction.REMicroOp.UP) {
            // TODO: Cleanup indexing of substate class
            UpSubstate upSubstate = inst.data();
            var buf = upSubstate.getSubstateBuffer();
            nextParticle = validationState.deserialization.deserialize(buf);
            if (buf.hasRemaining()) {
              throw new TrailingBytesException("Substate has trailing bytes.");
            }
            substateId = upSubstate.substateId();
            substateBuffer = upSubstate::getSubstateBuffer;
            validationState.bootUp(
                new Substate(nextParticle, substateId), upSubstate::getSubstateBuffer);
          } else if (inst.microOp() == REInstruction.REMicroOp.VDOWN) {
            substateId = inst.data();
            substateBuffer = null;
            nextParticle = validationState.virtualShutdown(substateId);
          } else if (inst.microOp() == REInstruction.REMicroOp.DOWN) {
            substateId = inst.data();
            substateBuffer = null;
            nextParticle = validationState.shutdown(substateId);
          } else if (inst.microOp() == REInstruction.REMicroOp.LDOWN) {
            substateId = inst.data();
            substateBuffer = null;
            nextParticle = validationState.localShutdown(substateId.getIndex().orElseThrow());
          } else if (inst.microOp() == REInstruction.REMicroOp.LVDOWN) {
            substateId = inst.data();
            substateBuffer = null;
            nextParticle = validationState.localVirtualShutdown(substateId);
          } else {
            throw new IllegalStateException("Unhandled op: " + inst.microOp());
          }

          var op = inst.microOp().getOp();
          var typeByte = deserialization.classToByte(nextParticle.getClass());
          stateUpdates.add(
              REStateUpdate.of(op, instIndex, substateId, typeByte, nextParticle, substateBuffer));
          var eventId = OpSignature.ofSubstateUpdate(op, nextParticle.getClass());
          var methodProcedure = loadProcedure(reducerState, eventId);
          reducerState =
              callProcedure(methodProcedure, nextParticle, reducerState, readableAddrs, context);
          expectEnd = reducerState == null;
        } else if (inst.microOp() == REInstruction.REMicroOp.END) {
          groupedStateUpdates.add(stateUpdates);
          stateUpdates = new ArrayList<>();

          if (reducerState != null) {
            var eventId = OpSignature.ofSubstateUpdate(inst.microOp().getOp(), null);
            var methodProcedure = loadProcedure(reducerState, eventId);
            reducerState =
                callProcedure(methodProcedure, reducerState, reducerState, readableAddrs, context);
          }

          expectEnd = false;
        } else if (inst.microOp() == REInstruction.REMicroOp.SIG) {
          if (context.permissionLevel() != PermissionLevel.SYSTEM) {
            meter.onSigInstruction(context);
          }
        } else {
          // Collect no-ops here
          if (inst.microOp() != REInstruction.REMicroOp.MSG
              && inst.microOp() != REInstruction.REMicroOp.HEADER) {
            throw new ProcedureException("Unknown op " + inst.microOp());
          }
        }
      } catch (Exception e) {
        throw new ConstraintMachineException(instIndex, instructions, reducerState, e);
      }

      instIndex++;
    }

    try {
      context.destroy();
    } catch (Exception e) {
      throw new ConstraintMachineException(instIndex, instructions, reducerState, e);
    }

    return groupedStateUpdates;
  }

  /**
   * Validates a CM instruction and calculates the necessary state checks and post-validation write
   * logic.
   *
   * @return the first error found, otherwise an empty optional
   */
  public List<List<REStateUpdate>> verify(
      CMStore cmStore, ExecutionContext context, List<REInstruction> instructions)
      throws TxnParseException, ConstraintMachineException {
    var validationState =
        new CMValidationState(virtualSubstateDeserialization, deserialization, cmStore);
    return this.statefulVerify(context, validationState, instructions);
  }
}
