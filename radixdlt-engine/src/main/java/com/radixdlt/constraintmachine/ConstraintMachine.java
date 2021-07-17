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

package com.radixdlt.constraintmachine;

import com.radixdlt.application.system.state.VirtualParent;
import com.radixdlt.atom.Substate;
import com.radixdlt.atom.CloseableCursor;
import com.radixdlt.atom.SubstateId;
import com.radixdlt.application.tokens.state.TokenResource;
import com.radixdlt.constraintmachine.exceptions.AuthorizationException;
import com.radixdlt.constraintmachine.exceptions.ConstraintMachineException;
import com.radixdlt.constraintmachine.exceptions.InvalidPermissionException;
import com.radixdlt.constraintmachine.exceptions.LocalSubstateNotFoundException;
import com.radixdlt.constraintmachine.exceptions.MeterException;
import com.radixdlt.constraintmachine.exceptions.MissingProcedureException;
import com.radixdlt.constraintmachine.exceptions.NotAResourceException;
import com.radixdlt.constraintmachine.exceptions.ProcedureException;
import com.radixdlt.constraintmachine.exceptions.SignedSystemException;
import com.radixdlt.constraintmachine.exceptions.SubstateNotFoundException;
import com.radixdlt.constraintmachine.exceptions.VirtualParentStateDoesNotExist;
import com.radixdlt.constraintmachine.exceptions.VirtualSubstateAlreadyDownException;
import com.radixdlt.engine.parser.exceptions.TrailingBytesException;
import com.radixdlt.engine.parser.exceptions.TxnParseException;
import com.radixdlt.constraintmachine.meter.Meter;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.serialization.DeserializeException;
import com.radixdlt.store.CMStore;
import com.radixdlt.utils.Pair;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

/**
 * An implementation of a UTXO based constraint machine which uses Radix's atom structure.
 */
// FIXME: unchecked, rawtypes
@SuppressWarnings({"unchecked", "rawtypes"})
public final class ConstraintMachine {
	private final Procedures procedures;
	private final VirtualSubstateDeserialization virtualSubstateDeserialization;
	private final SubstateDeserialization deserialization;
	private final Meter metering;

	public ConstraintMachine(
		Procedures procedures,
		SubstateDeserialization deserialization,
		VirtualSubstateDeserialization virtualSubstateDeserialization
	) {
		this(procedures, deserialization, virtualSubstateDeserialization, Meter.EMPTY);
	}

	public ConstraintMachine(
		Procedures procedures,
		SubstateDeserialization deserialization,
		VirtualSubstateDeserialization virtualSubstateDeserialization,
		Meter metering
	) {
		this.procedures = Objects.requireNonNull(procedures);
		this.deserialization = deserialization;
		this.virtualSubstateDeserialization = virtualSubstateDeserialization;
		this.metering = Objects.requireNonNull(metering);
	}

	public SubstateDeserialization getDeserialization() {
		return deserialization;
	}

	private static final class CMValidationState {
		private final Map<REAddr, TokenResource> localResources = new HashMap<>();
		private final Map<Integer, Pair<Substate, Supplier<ByteBuffer>>> localUpParticles = new HashMap<>();
		private final Set<SubstateId> remoteDownParticles = new HashSet<>();
		private final CMStore store;
		private final SubstateDeserialization deserialization;
		private final VirtualSubstateDeserialization virtualSubstateDeserialization;
		private int bootupCount = 0;

		CMValidationState(
			VirtualSubstateDeserialization virtualSubstateDeserialization,
			SubstateDeserialization deserialization,
			CMStore store
		) {
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

				var p = store.loadResource(addr).map(b -> {
					try {
						return deserialization.deserialize(b);
					} catch (DeserializeException e) {
						throw new IllegalStateException(e);
					}
				});
				if (p.isEmpty()) {
					throw new NotAResourceException(addr);
				}
				var substate = p.get();
				if (!(substate instanceof TokenResource)) {
					throw new NotAResourceException(addr);
				}
				return (TokenResource) substate;
			};
		}

		public Optional<Particle> loadUpParticle(SubstateId substateId) {
			if (remoteDownParticles.contains(substateId)) {
				return Optional.empty();
			}

			var raw = store.loadSubstate(substateId);
			return raw.map(b -> {
				try {
					return deserialization.deserialize(b);
				} catch (DeserializeException e) {
					throw new IllegalStateException(e);
				}
			});
		}

		public void bootUp(Substate substate, Supplier<ByteBuffer> buffer) {
			localUpParticles.put(bootupCount, Pair.of(substate, buffer));
			if (substate.getParticle() instanceof TokenResource) {
				var resource = (TokenResource) substate.getParticle();
				localResources.put(resource.getAddr(), resource);
			}
			bootupCount++;
		}

		public Particle virtualRead(SubstateId substateId)
			throws VirtualSubstateAlreadyDownException, VirtualParentStateDoesNotExist, DeserializeException {
			if (remoteDownParticles.contains(substateId)) {
				throw new VirtualSubstateAlreadyDownException(substateId);
			}

			var parentBuf = store.verifyVirtualSubstate(substateId);
			var parent = (VirtualParent) deserialization.deserialize(parentBuf);
			var typeByte = parent.getData()[0];
			var keyBuf = substateId.getVirtualKey().orElseThrow();
			return virtualSubstateDeserialization.keyToSubstate(typeByte, keyBuf);
		}

		public Particle virtualShutdown(SubstateId substateId)
			throws VirtualSubstateAlreadyDownException, VirtualParentStateDoesNotExist, DeserializeException {
			var p = virtualRead(substateId);
			remoteDownParticles.add(substateId);
			return p;
		}


		public Particle localVirtualRead(SubstateId substateId)
			throws VirtualSubstateAlreadyDownException, VirtualParentStateDoesNotExist, DeserializeException {
			if (remoteDownParticles.contains(substateId)) {
				throw new VirtualSubstateAlreadyDownException(substateId);
			}

			var parentId = substateId.getVirtualParent().orElseThrow();
			var substate = localUpParticles.get(parentId.getIndex().orElseThrow());
			if (substate == null || !(substate.getFirst().getParticle() instanceof VirtualParent)) {
				throw new VirtualParentStateDoesNotExist(parentId);
			}
			var parent = (VirtualParent) substate.getFirst().getParticle();
			var typeByte = parent.getData()[0];
			var keyBuf = substateId.getVirtualKey().orElseThrow();
			return virtualSubstateDeserialization.keyToSubstate(typeByte, keyBuf);
		}

		public Particle localVirtualShutdown(SubstateId substateId)
			throws VirtualSubstateAlreadyDownException, VirtualParentStateDoesNotExist, DeserializeException {
			var p = localVirtualRead(substateId);
			remoteDownParticles.add(substateId);
			return p;
		}

		public Particle localShutdown(int index) throws LocalSubstateNotFoundException {
			var substate = localUpParticles.remove(index);
			if (substate == null) {
				throw new LocalSubstateNotFoundException(index);
			}

			return substate.getFirst().getParticle();
		}

		public Particle localRead(int index) throws LocalSubstateNotFoundException {
			var substate = localUpParticles.get(index);
			if (substate == null) {
				throw new LocalSubstateNotFoundException(index);
			}

			return substate.getFirst().getParticle();
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

		public CloseableCursor<Substate> getIndexedCursor(SubstateIndex index) {
			return CloseableCursor.wrapIterator(localUpParticles.values().stream()
					.filter(s -> index.test(s.getSecond().get())).map(Pair::getFirst).iterator()
				).concat(() -> store.openIndexedCursor(index)
					.map(r -> {
						try {
							var substate = deserialization.deserialize(r.getData());
							return Substate.create(substate, SubstateId.fromBytes(r.getId()));
						} catch (DeserializeException e) {
							throw new IllegalStateException();
						}
					})
					.filter(s -> !remoteDownParticles.contains(s.getId()))
				);
		}
	}

	private Procedure loadProcedure(
		ReducerState reducerState,
		OpSignature opSignature
	) throws MissingProcedureException {
		var reducerStateClass = reducerState != null
			? reducerState.getClass()
			: VoidReducerState.class;
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
		ExecutionContext context
	) throws SignedSystemException, InvalidPermissionException, AuthorizationException, MeterException, ProcedureException {
		// System permissions don't require additional authorization
		var authorization = procedure.authorization(procedureParam);
		var requiredLevel = authorization.permissionLevel();
		context.verifyPermissionLevel(requiredLevel);
		if (context.permissionLevel() != PermissionLevel.SYSTEM) {
			try {
				if (requiredLevel == PermissionLevel.USER) {
					this.metering.onUserProcedure(procedure.key(), procedureParam, context);
				} else if (requiredLevel == PermissionLevel.SUPER_USER) {
					this.metering.onSuperUserProcedure(procedure.key(), procedureParam, context);
				}
			} catch (Exception e) {
				throw new MeterException(e);
			}

			try {
				authorization.authorizer().verify(immutableAddrs, context);
			} catch (Exception e) {
				throw new AuthorizationException(e);
			}
		}

		return procedure.call(procedureParam, reducerState, immutableAddrs, context).state();
	}

	private static class MissingExpectedEndException extends Exception {
	}

	/**
	 * Executes transition procedures and witness validators in a particle group and validates
	 * that the particle group is well formed.
	 */
	List<List<REStateUpdate>> statefulVerify(
		ExecutionContext context,
		CMValidationState validationState,
		List<REInstruction> instructions
	) throws ConstraintMachineException {
		int instIndex = 0;
		var expectEnd = false;
		ReducerState reducerState = null;
		var readableAddrs = validationState.resources();
		var groupedStateUpdates = new ArrayList<List<REStateUpdate>>();
		var stateUpdates = new ArrayList<REStateUpdate>();

		for (REInstruction inst : instructions) {
			try {
				if (expectEnd && inst.getMicroOp() != REInstruction.REMicroOp.END) {
					throw new MissingExpectedEndException();
				}

				if (inst.getMicroOp() == REInstruction.REMicroOp.SYSCALL) {
					CallData callData = inst.getData();
					var opSignature = OpSignature.ofMethod(inst.getMicroOp().getOp(), REAddr.ofSystem());
					var methodProcedure = loadProcedure(reducerState, opSignature);
					reducerState = callProcedure(methodProcedure, callData, reducerState, readableAddrs, context);
				} else if (inst.getMicroOp().getOp() == REOp.READ) {
					final Particle nextParticle;
					if (inst.getMicroOp() == REInstruction.REMicroOp.VREAD) {
						SubstateId substateId = inst.getData();
						nextParticle = validationState.virtualRead(substateId);
					} else if (inst.getMicroOp() == REInstruction.REMicroOp.READ) {
						SubstateId substateId = inst.getData();
						nextParticle = validationState.read(substateId);
					} else if (inst.getMicroOp() == REInstruction.REMicroOp.LREAD) {
						SubstateId substateId = inst.getData();
						nextParticle = validationState.localRead(substateId.getIndex().orElseThrow());
					} else if (inst.getMicroOp() == REInstruction.REMicroOp.LVREAD) {
						SubstateId substateId = inst.getData();
						nextParticle = validationState.localVirtualRead(substateId);
					} else {
						throw new IllegalStateException("Unknown read op " + inst.getMicroOp());
					}
					var eventId = OpSignature.ofSubstateUpdate(inst.getMicroOp().getOp(), nextParticle.getClass());
					var methodProcedure = loadProcedure(reducerState, eventId);
					reducerState = callProcedure(methodProcedure, nextParticle, reducerState, readableAddrs, context);
					expectEnd = reducerState == null;
				} else if (inst.getMicroOp().getOp() == REOp.DOWNINDEX || inst.getMicroOp().getOp() == REOp.READINDEX) {
					byte[] raw = inst.getData();
					var index = SubstateIndex.create(raw, validationState.deserialization.byteToClass(raw[0]));
					var substateCursor = validationState.getIndexedCursor(index);
					var tmp = stateUpdates;
					var iterator = new Iterator<Particle>() {
						@Override
						public boolean hasNext() {
							return substateCursor.hasNext();
						}

						@Override
						public Particle next() {
							// FIXME: this is a hack
							// FIXME: do this via shutdownAll state update rather than individually
							var substate = substateCursor.next();
							if (inst.getMicroOp().getOp() == REOp.DOWNINDEX) {
								var typeByte = deserialization.classToByte(substate.getParticle().getClass());
								tmp.add(REStateUpdate.of(REOp.DOWN, substate.getId(), typeByte, substate.getParticle(), null));
							}
							return substate.getParticle();
						}
					};
					var substateIterator = new IndexedSubstateIterator<>(index, iterator);
					try {
						var eventId = OpSignature.ofSubstateUpdate(
							inst.getMicroOp().getOp(), index.getSubstateClass()
						);
						var methodProcedure = loadProcedure(reducerState, eventId);
						reducerState = callProcedure(methodProcedure, substateIterator, reducerState, readableAddrs, context);
					} finally {
						substateCursor.close();
					}
				} else if (inst.isStateUpdate()) {
					final SubstateId substateId;
					final Particle nextParticle;
					final Supplier<ByteBuffer> substateBuffer;
					if (inst.getMicroOp() == REInstruction.REMicroOp.UP) {
						// TODO: Cleanup indexing of substate class
						UpSubstate upSubstate = inst.getData();
						var buf = upSubstate.getSubstateBuffer();
						nextParticle = validationState.deserialization.deserialize(buf);
						if (buf.hasRemaining()) {
							throw new TrailingBytesException("Substate has trailing bytes.");
						}
						substateId = upSubstate.getSubstateId();
						substateBuffer = upSubstate::getSubstateBuffer;
						validationState.bootUp(Substate.create(nextParticle, substateId), upSubstate::getSubstateBuffer);
					} else if (inst.getMicroOp() == REInstruction.REMicroOp.VDOWN) {
						substateId = inst.getData();
						substateBuffer = null;
						nextParticle = validationState.virtualShutdown(substateId);
					} else if (inst.getMicroOp() == REInstruction.REMicroOp.DOWN) {
						substateId = inst.getData();
						substateBuffer = null;
						nextParticle = validationState.shutdown(substateId);
					} else if (inst.getMicroOp() == REInstruction.REMicroOp.LDOWN) {
						substateId = inst.getData();
						substateBuffer = null;
						nextParticle = validationState.localShutdown(substateId.getIndex().orElseThrow());
					} else if (inst.getMicroOp() == REInstruction.REMicroOp.LVDOWN) {
						substateId = inst.getData();
						substateBuffer = null;
						nextParticle = validationState.localVirtualShutdown(substateId);
					} else {
						throw new IllegalStateException("Unhandled op: " + inst.getMicroOp());
					}

					var op = inst.getMicroOp().getOp();
					var typeByte = deserialization.classToByte(nextParticle.getClass());
					stateUpdates.add(REStateUpdate.of(op, substateId, typeByte, nextParticle, substateBuffer));
					var eventId = OpSignature.ofSubstateUpdate(op, nextParticle.getClass());
					var methodProcedure = loadProcedure(reducerState, eventId);
					reducerState = callProcedure(methodProcedure, nextParticle, reducerState, readableAddrs, context);
					expectEnd = reducerState == null;
				} else if (inst.getMicroOp() == REInstruction.REMicroOp.END) {
					groupedStateUpdates.add(stateUpdates);
					stateUpdates = new ArrayList<>();

					if (reducerState != null) {
						var eventId = OpSignature.ofSubstateUpdate(inst.getMicroOp().getOp(), null);
						var methodProcedure = loadProcedure(reducerState, eventId);
						reducerState = callProcedure(methodProcedure, reducerState, reducerState, readableAddrs, context);
					}

					expectEnd = false;
				} else if (inst.getMicroOp() == REInstruction.REMicroOp.SIG) {
					if (context.permissionLevel() != PermissionLevel.SYSTEM) {
						metering.onSigInstruction(context);
					}
				} else {
					// Collect no-ops here
					if (inst.getMicroOp() != REInstruction.REMicroOp.MSG
						&& inst.getMicroOp() != REInstruction.REMicroOp.HEADER) {
						throw new ProcedureException("Unknown op " + inst.getMicroOp());
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
	 * Validates a CM instruction and calculates the necessary state checks and post-validation
	 * write logic.
	 *
	 * @return the first error found, otherwise an empty optional
	 */
	public List<List<REStateUpdate>> verify(
		CMStore cmStore,
		ExecutionContext context,
		List<REInstruction> instructions
	) throws TxnParseException, ConstraintMachineException {
		var validationState = new CMValidationState(virtualSubstateDeserialization, deserialization, cmStore);
		return this.statefulVerify(context, validationState, instructions);
	}
}
