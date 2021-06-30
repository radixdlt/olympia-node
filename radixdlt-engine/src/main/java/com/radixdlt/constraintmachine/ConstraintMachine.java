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

import com.radixdlt.atom.Substate;
import com.radixdlt.atom.CloseableCursor;
import com.radixdlt.atom.SubstateId;
import com.radixdlt.application.tokens.state.TokenResource;
import com.radixdlt.constraintmachine.exceptions.AuthorizationException;
import com.radixdlt.constraintmachine.exceptions.ConstraintMachineException;
import com.radixdlt.constraintmachine.exceptions.InvalidPermissionException;
import com.radixdlt.constraintmachine.exceptions.InvalidVirtualSubstateException;
import com.radixdlt.constraintmachine.exceptions.LocalSubstateNotFoundException;
import com.radixdlt.constraintmachine.exceptions.MeterException;
import com.radixdlt.constraintmachine.exceptions.MissingProcedureException;
import com.radixdlt.constraintmachine.exceptions.ProcedureException;
import com.radixdlt.constraintmachine.exceptions.SignedSystemException;
import com.radixdlt.constraintmachine.exceptions.SubstateNotFoundException;
import com.radixdlt.constraintmachine.exceptions.TxnParseException;
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
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * An implementation of a UTXO based constraint machine which uses Radix's atom structure.
 */
// FIXME: unchecked, rawtypes
@SuppressWarnings({"unchecked", "rawtypes"})
public final class ConstraintMachine {
	private final Predicate<Particle> virtualStoreLayer;
	private final Procedures procedures;
	private final Meter metering;

	public ConstraintMachine(
		Predicate<Particle> virtualStoreLayer,
		Procedures procedures
	) {
		this(virtualStoreLayer, procedures, Meter.EMPTY);
	}

	public ConstraintMachine(
		Predicate<Particle> virtualStoreLayer,
		Procedures procedures,
		Meter metering
	) {
		this.virtualStoreLayer = Objects.requireNonNull(virtualStoreLayer);
		this.procedures = Objects.requireNonNull(procedures);
		this.metering = Objects.requireNonNull(metering);
	}

	private static final class CMValidationState {
		private final Map<Integer, Pair<Substate, Supplier<ByteBuffer>>> localUpParticles = new HashMap<>();
		private final Set<SubstateId> remoteDownParticles = new HashSet<>();
		private final CMStore store;
		private final Predicate<Particle> virtualStoreLayer;
		private final SubstateDeserialization deserialization;
		private int bootupCount = 0;

		CMValidationState(
			SubstateDeserialization deserialization,
			Predicate<Particle> virtualStoreLayer,
			CMStore store
		) {
			this.deserialization = deserialization;
			this.virtualStoreLayer = virtualStoreLayer;
			this.store = store;
		}

		public ImmutableAddrs immutableAddrs() {
			return addr ->
				localUpParticles.values().stream()
					.map(Pair::getFirst)
					.map(Substate::getParticle)
					.filter(TokenResource.class::isInstance)
					.map(TokenResource.class::cast)
					.filter(p -> p.getAddr().equals(addr))
					.findFirst()
					.map(Particle.class::cast)
					.or(() -> store.loadAddr(addr, deserialization));
		}

		public Optional<Particle> loadUpParticle(SubstateId substateId) {
			if (remoteDownParticles.contains(substateId)) {
				return Optional.empty();
			}

			return store.loadUpParticle(substateId, deserialization);
		}

		public void bootUp(Substate substate, Supplier<ByteBuffer> buffer) {
			localUpParticles.put(bootupCount, Pair.of(substate, buffer));
			bootupCount++;
		}

		public void virtualRead(Substate substate) throws SubstateNotFoundException, InvalidVirtualSubstateException {
			if (remoteDownParticles.contains(substate.getId())) {
				throw new SubstateNotFoundException(substate.getId());
			}

			if (!virtualStoreLayer.test(substate.getParticle())) {
				throw new InvalidVirtualSubstateException(substate.getParticle());
			}

			if (store.isVirtualDown(substate.getId())) {
				throw new SubstateNotFoundException(substate.getId());
			}
		}

		public void virtualShutdown(Substate substate) throws SubstateNotFoundException, InvalidVirtualSubstateException {
			virtualRead(substate);
			remoteDownParticles.add(substate.getId());
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

		public CloseableCursor<Substate> shutdownAll(SubstateIndex index) {
			return CloseableCursor.concat(
				CloseableCursor.wrapIterator(localUpParticles.values().stream()
					.filter(s -> index.test(s.getSecond().get())).map(Pair::getFirst).iterator()
				),
				() -> CloseableCursor.filter(
					CloseableCursor.map(
						store.openIndexedCursor(index),
						r -> {
							try {
								var substate = deserialization.deserialize(r.getData());
								return Substate.create(substate, SubstateId.fromBytes(r.getId()));
							} catch (DeserializeException e) {
								throw new IllegalStateException();
							}
						}),
					s -> !remoteDownParticles.contains(s.getId())
				)
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
		ImmutableAddrs immutableAddrs,
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

			authorization.authorizer().verify(immutableAddrs, context);
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
		var readableAddrs = validationState.immutableAddrs();
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
						Substate substate = inst.getData();
						nextParticle = substate.getParticle();
						validationState.virtualRead(substate);
					} else if (inst.getMicroOp() == REInstruction.REMicroOp.READ) {
						SubstateId substateId = inst.getData();
						nextParticle = validationState.read(substateId);
					} else if (inst.getMicroOp() == REInstruction.REMicroOp.LREAD) {
						SubstateId substateId = inst.getData();
						nextParticle = validationState.localRead(substateId.getIndex().orElseThrow());
					} else {
						throw new IllegalStateException("Unknown read op");
					}
					var eventId = OpSignature.ofSubstateUpdate(inst.getMicroOp().getOp(), nextParticle.getClass());
					var methodProcedure = loadProcedure(reducerState, eventId);
					reducerState = callProcedure(methodProcedure, nextParticle, reducerState, readableAddrs, context);
					expectEnd = reducerState == null;
				} else if (inst.getMicroOp().getOp() == REOp.DOWNINDEX) {
					SubstateIndex index = inst.getData();
					var substateCursor = validationState.shutdownAll(index);
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
							tmp.add(REStateUpdate.of(REOp.DOWN, substate, null, inst::getDataByteBuffer));
							return substate.getParticle();
						}
					};
					var shutdownAllIterator = new ShutdownAll<>(index, iterator);
					try {
						var eventId = OpSignature.ofSubstateUpdate(
							inst.getMicroOp().getOp(), index.getSubstateClass()
						);
						var methodProcedure = loadProcedure(reducerState, eventId);
						reducerState = callProcedure(methodProcedure, shutdownAllIterator, reducerState, readableAddrs, context);
					} finally {
						substateCursor.close();
					}
				} else if (inst.isStateUpdate()) {
					final Particle nextParticle;
					final Substate substate;
					final byte[] arg;
					final Object o;
					if (inst.getMicroOp() == REInstruction.REMicroOp.UP) {
						// TODO: Cleanup indexing of substate class
						substate = inst.getData();
						arg = null;
						nextParticle = substate.getParticle();
						o = nextParticle;
						validationState.bootUp(substate, inst::getDataByteBuffer);
					} else if (inst.getMicroOp() == REInstruction.REMicroOp.VDOWN) {
						substate = inst.getData();
						arg = null;
						nextParticle = substate.getParticle();
						o = SubstateWithArg.noArg(nextParticle);
						validationState.virtualShutdown(substate);
					} else if (inst.getMicroOp() == REInstruction.REMicroOp.VDOWNARG) {
						substate = (Substate) ((Pair) inst.getData()).getFirst();
						arg = (byte[]) ((Pair) inst.getData()).getSecond();
						nextParticle = substate.getParticle();
						o = SubstateWithArg.withArg(nextParticle, arg);
						validationState.virtualShutdown(substate);
					} else if (inst.getMicroOp() == REInstruction.REMicroOp.DOWN) {
						SubstateId substateId = inst.getData();
						nextParticle = validationState.shutdown(substateId);
						substate = Substate.create(nextParticle, substateId);
						arg = null;
						o = SubstateWithArg.noArg(nextParticle);
					} else if (inst.getMicroOp() == REInstruction.REMicroOp.LDOWN) {
						SubstateId substateId = inst.getData();
						nextParticle = validationState.localShutdown(substateId.getIndex().orElseThrow());
						substate = Substate.create(nextParticle, substateId);
						arg = null;
						o = SubstateWithArg.noArg(nextParticle);
					} else {
						throw new IllegalStateException("Unhandled op: " + inst.getMicroOp());
					}

					var op = inst.getMicroOp().getOp();
					stateUpdates.add(REStateUpdate.of(op, substate, arg, inst::getDataByteBuffer));
					var eventId = OpSignature.ofSubstateUpdate(op, nextParticle.getClass());
					var methodProcedure = loadProcedure(reducerState, eventId);
					reducerState = callProcedure(methodProcedure, o, reducerState, readableAddrs, context);
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
					metering.onSigInstruction(context);
				}
			} catch (Exception e) {
				throw new ConstraintMachineException(instIndex, inst, reducerState, e);
			}

			instIndex++;
		}

		try {
			context.destroy();
		} catch (Exception e) {
			throw new ConstraintMachineException(instIndex, null, reducerState, e);
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
		SubstateDeserialization deserialization,
		CMStore cmStore,
		ExecutionContext context,
		List<REInstruction> instructions
	) throws TxnParseException, ConstraintMachineException {
		var validationState = new CMValidationState(deserialization, virtualStoreLayer, cmStore);
		return this.statefulVerify(context, validationState, instructions);
	}
}
