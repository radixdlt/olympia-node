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
import com.radixdlt.atommodel.system.state.SystemParticle;
import com.radixdlt.atommodel.tokens.state.TokenResource;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.store.CMStore;
import com.radixdlt.utils.Pair;
import com.radixdlt.utils.UInt256;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

/**
 * An implementation of a UTXO based constraint machine which uses Radix's atom structure.
 */
// FIXME: unchecked, rawtypes
@SuppressWarnings({"unchecked", "rawtypes"})
public final class ConstraintMachine {
	private final Predicate<Particle> virtualStoreLayer;
	private final Procedures procedures;
	private final Metering metering;

	public ConstraintMachine(
		Predicate<Particle> virtualStoreLayer,
		Procedures procedures
	) {
		this.virtualStoreLayer = virtualStoreLayer;
		this.procedures = procedures;
		this.metering = (k, param, context) -> { };
	}

	public ConstraintMachine(
		Predicate<Particle> virtualStoreLayer,
		Procedures procedures,
		Metering metering
	) {
		this.virtualStoreLayer = virtualStoreLayer;
		this.procedures = procedures;
		this.metering = metering;
	}

	private static final class CMValidationState {
		private final Map<Integer, Substate> localUpParticles = new HashMap<>();
		private final Set<SubstateId> remoteDownParticles = new HashSet<>();
		private final CMStore store;
		private final CMStore.Transaction dbTxn;
		private final Predicate<Particle> virtualStoreLayer;
		private final SubstateDeserialization deserialization;

		CMValidationState(
			SubstateDeserialization deserialization,
			Predicate<Particle> virtualStoreLayer,
			CMStore.Transaction dbTxn,
			CMStore store
		) {
			this.deserialization = deserialization;
			this.virtualStoreLayer = virtualStoreLayer;
			this.dbTxn = dbTxn;
			this.store = store;
		}

		public ReadableAddrs readableAddrs() {
			// TODO: Fix ReadableAddrs interface (remove txn)
			return addr -> {
				if (addr.isSystem()) {
					return localUpParticles.values().stream()
						.map(Substate::getParticle)
						.filter(SystemParticle.class::isInstance)
						.findFirst()
						.or(() -> store.loadAddr(dbTxn, addr, deserialization))
						.or(() -> Optional.of(new SystemParticle(0, 0, 0))); // A bit of a hack
				} else {
					return localUpParticles.values().stream()
						.map(Substate::getParticle)
						.filter(TokenResource.class::isInstance)
						.map(TokenResource.class::cast)
						.filter(p -> p.getAddr().equals(addr))
						.findFirst()
						.map(Particle.class::cast)
						.or(() -> store.loadAddr(dbTxn, addr, deserialization));
				}
			};
		}

		public Optional<Particle> loadUpParticle(SubstateId substateId) {
			if (remoteDownParticles.contains(substateId)) {
				return Optional.empty();
			}

			return store.loadUpParticle(dbTxn, substateId, deserialization);
		}

		public void bootUp(int instructionIndex, Substate substate) {
			localUpParticles.put(instructionIndex, substate);
		}

		public void virtualShutdown(Substate substate) throws ConstraintMachineException {
			if (remoteDownParticles.contains(substate.getId())) {
				throw new ConstraintMachineException(CMErrorCode.SUBSTATE_NOT_FOUND);
			}

			if (!virtualStoreLayer.test(substate.getParticle())) {
				throw new ConstraintMachineException(CMErrorCode.INVALID_PARTICLE);
			}

			if (store.isVirtualDown(dbTxn, substate.getId())) {
				throw new ConstraintMachineException(CMErrorCode.SUBSTATE_NOT_FOUND);
			}

			remoteDownParticles.add(substate.getId());
		}

		public Particle localShutdown(int index) throws ConstraintMachineException {
			var substate = localUpParticles.remove(index);
			if (substate == null) {
				throw new ConstraintMachineException(CMErrorCode.LOCAL_NONEXISTENT);
			}

			return substate.getParticle();
		}

		public Optional<Particle> read(SubstateId substateId) {
			return loadUpParticle(substateId);
		}

		public Particle shutdown(SubstateId substateId) throws ConstraintMachineException {
			var maybeParticle = loadUpParticle(substateId);
			if (maybeParticle.isEmpty()) {
				throw new ConstraintMachineException(CMErrorCode.SUBSTATE_NOT_FOUND);
			}
			remoteDownParticles.add(substateId);
			return maybeParticle.get();
		}

		public CloseableCursor<Substate> shutdownAll(DownAllIndex index) {
			return CloseableCursor.concat(
				CloseableCursor.wrapIterator(localUpParticles.values().stream()
					.filter(s -> index.test(s.getParticle())).iterator()
				),
				() -> CloseableCursor.filter(
					store.openIndexedCursor(dbTxn, index.getIndex(), deserialization),
					s -> !remoteDownParticles.contains(s.getId())
				)
			);
		}
	}

	private Procedure loadProcedure(
		ReducerState reducerState,
		OpSignature opSignature
	) throws ConstraintMachineException {
		try {
			var reducerStateClass = reducerState != null
				? reducerState.getClass()
				: VoidReducerState.class;
			var key = ProcedureKey.of(reducerStateClass, opSignature);
			return this.procedures.getProcedure(key);
		} catch (MissingProcedureException e) {
			throw new ConstraintMachineException(CMErrorCode.MISSING_PROCEDURE, e);
		}
	}

	/**
	 * Executes a transition procedure given the next spun particle and a current validation state.
	 */
	private ReducerState callProcedure(
		Procedure procedure,
		Object procedureParam,
		ReducerState reducerState,
		ReadableAddrs readableAddrs,
		ExecutionContext context
	) throws ConstraintMachineException {
		// System permissions don't require additional authorization
		var authorization = procedure.authorization(procedureParam);
		var requiredLevel = authorization.permissionLevel();
		context.verifyPermissionLevel(requiredLevel);
		try {
			if (context.permissionLevel() != PermissionLevel.SYSTEM) {
				if (requiredLevel == PermissionLevel.USER) {
					this.metering.onUserInstruction(procedure.key(), procedureParam, context);
				}

				authorization.authorizer().verify(readableAddrs, context);
			}

			return procedure.call(procedureParam, reducerState, readableAddrs, context).state();
		} catch (AuthorizationException e) {
			throw new ConstraintMachineException(CMErrorCode.AUTHORIZATION_ERROR, e);
		} catch (ProcedureException e) {
			throw new ConstraintMachineException(CMErrorCode.PROCEDURE_ERROR, e);
		} catch (Exception e) {
			throw new ConstraintMachineException(CMErrorCode.UNKNOWN_ERROR, e);
		}
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
		var readableAddrs = validationState.readableAddrs();
		var groupedStateUpdates = new ArrayList<List<REStateUpdate>>();
		var stateUpdates = new ArrayList<REStateUpdate>();

		for (REInstruction inst : instructions) {
			if (expectEnd && inst.getMicroOp() != REInstruction.REMicroOp.END) {
				throw new ConstraintMachineException(CMErrorCode.MISSING_PARTICLE_GROUP);
			}

			if (inst.getMicroOp() == REInstruction.REMicroOp.SYSCALL) {
				CallData callData = inst.getData();
				var opSignature = OpSignature.ofMethod(inst.getMicroOp().getOp(), REAddr.ofSystem());
				var methodProcedure = loadProcedure(reducerState, opSignature);
				reducerState = callProcedure(methodProcedure, callData, reducerState, readableAddrs, context);
			} else if (inst.getMicroOp() == REInstruction.REMicroOp.DOWNALL) {
				DownAllIndex index = inst.getData();
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
						tmp.add(REStateUpdate.of(REOp.DOWN, substate, null, inst.getDataByteBuffer()));
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
					validationState.bootUp(instIndex, substate);
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
					throw new ConstraintMachineException(CMErrorCode.UNKNOWN_OP);
				}

				var op = inst.getMicroOp().getOp();
				stateUpdates.add(REStateUpdate.of(op, substate, arg, inst.getDataByteBuffer()));
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
			}

			instIndex++;
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
		CMStore.Transaction dbTxn,
		SubstateDeserialization deserialization,
		CMStore cmStore,
		PermissionLevel permissionLevel,
		List<REInstruction> instructions,
		Optional<ECPublicKey> signature,
		boolean disableResourceAllocAndDestroy
	) throws TxnParseException, ConstraintMachineException {
		var validationState = new CMValidationState(deserialization, virtualStoreLayer, dbTxn, cmStore);
		var context = new ExecutionContext(permissionLevel, signature, UInt256.ZERO, disableResourceAllocAndDestroy);
		return this.statefulVerify(context, validationState, instructions);
	}
}
