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
import com.radixdlt.atom.SubstateCursor;
import com.radixdlt.atom.SubstateId;
import com.radixdlt.atommodel.system.state.SystemParticle;
import com.radixdlt.atommodel.tokens.state.TokenResource;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.store.CMStore;
import com.radixdlt.store.ReadableAddrs;
import com.radixdlt.utils.Pair;

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

	public ConstraintMachine(
		Predicate<Particle> virtualStoreLayer,
		Procedures procedures
	) {
		this.virtualStoreLayer = virtualStoreLayer;
		this.procedures = procedures;
	}

	public static final class CMValidationState {
		private final PermissionLevel permissionLevel;
		private REInstruction curInstruction;
		private int curIndex;

		private ReducerState reducerState = null;

		private final Map<Integer, Substate> localUpParticles = new HashMap<>();
		private final Set<SubstateId> remoteDownParticles = new HashSet<>();
		private final Optional<ECPublicKey> signedBy;
		private final CMStore store;
		private final CMStore.Transaction dbTxn;
		private final Predicate<Particle> virtualStoreLayer;

		CMValidationState(
			Predicate<Particle> virtualStoreLayer,
			CMStore.Transaction dbTxn,
			CMStore store,
			PermissionLevel permissionLevel,
			Optional<ECPublicKey> signedBy
		) {
			this.virtualStoreLayer = virtualStoreLayer;
			this.dbTxn = dbTxn;
			this.store = store;
			this.permissionLevel = permissionLevel;
			this.signedBy = signedBy;
		}

		public void verifyPermissionLevel(PermissionLevel requiredLevel) throws ConstraintMachineException {
			if (this.permissionLevel.compareTo(requiredLevel) < 0) {
				throw new ConstraintMachineException(
					CMErrorCode.PERMISSION_LEVEL_ERROR,
					this,
					"Required: " + requiredLevel + " Current: " + this.permissionLevel
				);
			}
		}

		public ReadableAddrs immutableIndex() {
			// TODO: Fix ReadableAddrs interface (remove txn)
			return (ignoredTxn, addr) -> {
				if (addr.isSystem()) {
					return localUpParticles.values().stream()
						.map(Substate::getParticle)
						.filter(SystemParticle.class::isInstance)
						.findFirst()
						.or(() -> store.loadAddr(dbTxn, addr))
						.or(() -> Optional.of(new SystemParticle(0, 0, 0))); // A bit of a hack
				} else {
					return localUpParticles.values().stream()
						.map(Substate::getParticle)
						.filter(TokenResource.class::isInstance)
						.map(TokenResource.class::cast)
						.filter(p -> p.getAddr().equals(addr))
						.findFirst()
						.map(Particle.class::cast)
						.or(() -> store.loadAddr(dbTxn, addr));
				}
			};
		}

		public Optional<Particle> loadUpParticle(SubstateId substateId) {
			if (remoteDownParticles.contains(substateId)) {
				return Optional.empty();
			}

			return store.loadUpParticle(dbTxn, substateId);
		}

		public void bootUp(int instructionIndex, Substate substate) {
			localUpParticles.put(instructionIndex, substate);
		}

		public void virtualShutdown(Substate substate) throws ConstraintMachineException {
			if (remoteDownParticles.contains(substate.getId())) {
				throw new ConstraintMachineException(CMErrorCode.SUBSTATE_NOT_FOUND, this);
			}

			if (!virtualStoreLayer.test(substate.getParticle())) {
				throw new ConstraintMachineException(CMErrorCode.INVALID_PARTICLE, this);
			}

			if (store.isVirtualDown(dbTxn, substate.getId())) {
				throw new ConstraintMachineException(CMErrorCode.SUBSTATE_NOT_FOUND, this);
			}

			remoteDownParticles.add(substate.getId());
		}

		public Particle localShutdown(int index) throws ConstraintMachineException {
			var substate = localUpParticles.remove(index);
			if (substate == null) {
				throw new ConstraintMachineException(CMErrorCode.LOCAL_NONEXISTENT, this);
			}

			return substate.getParticle();
		}

		public Optional<Particle> read(SubstateId substateId) {
			return loadUpParticle(substateId);
		}

		public Particle shutdown(SubstateId substateId) throws ConstraintMachineException {
			var maybeParticle = loadUpParticle(substateId);
			if (maybeParticle.isEmpty()) {
				throw new ConstraintMachineException(CMErrorCode.SUBSTATE_NOT_FOUND, this);
			}
			remoteDownParticles.add(substateId);
			return maybeParticle.get();
		}

		public SubstateCursor shutdownAll(Class<? extends Particle> particleClass) {
			return SubstateCursor.concat(
				SubstateCursor.wrapIterator(localUpParticles.values().stream()
					.filter(s -> particleClass.isInstance(s.getParticle())).iterator()
				),
				() -> SubstateCursor.filter(
					store.openIndexedCursor(dbTxn, particleClass),
					s -> !remoteDownParticles.contains(s.getId())
				)
			);
		}

		Class<? extends ReducerState> getReducerStateClass() {
			return reducerState != null ? reducerState.getClass() : VoidReducerState.class;
		}

		@Override
		public String toString() {
			return String.format("CMState{state=%s inst=%s}", this.reducerState, this.curInstruction);
		}
	}


	/**
	 * Executes a transition procedure given the next spun particle and a current validation state.
	 */
	void callProcedure(
		CMValidationState validationState,
		REInstruction.REOp op,
		Class<? extends Particle> particleClass,
		Object procedureParam
	) throws ConstraintMachineException {

		final MethodProcedure methodProcedure;
		try {
			var key = ProcedureKey.of(particleClass, validationState.getReducerStateClass());
			methodProcedure = this.procedures.getProcedure(op, key);
		} catch (MissingProcedureException e) {
			throw new ConstraintMachineException(CMErrorCode.MISSING_PROCEDURE, validationState, e);
		}

		// TODO: Reduce the 2 following procedures to 1
		final Object authorizationParam;
		if (op == REInstruction.REOp.END) {
			authorizationParam = validationState.reducerState;
		} else {
			authorizationParam = procedureParam;
		}

		var readable = validationState.immutableIndex();
		var reducerState = validationState.reducerState;
		final ReducerResult reducerResult;
		// System permissions don't require additional authorization
		if (validationState.permissionLevel != PermissionLevel.SYSTEM) {
			var requiredLevel = methodProcedure.permissionLevel(authorizationParam, readable);
			validationState.verifyPermissionLevel(requiredLevel);
		}

		try {
			if (validationState.permissionLevel != PermissionLevel.SYSTEM) {
				methodProcedure.verifyAuthorization(authorizationParam, readable, validationState.signedBy);
			}
			reducerResult = methodProcedure.call(procedureParam, reducerState, readable);
		} catch (AuthorizationException e) {
			throw new ConstraintMachineException(CMErrorCode.AUTHORIZATION_ERROR, validationState, e);
		} catch (ProcedureException e) {
			throw new ConstraintMachineException(CMErrorCode.PROCEDURE_ERROR, validationState, e);
		} catch (Exception e) {
			throw new ConstraintMachineException(CMErrorCode.UNKNOWN_ERROR, validationState, e);
		}

		reducerResult.ifCompleteElse(
			() -> validationState.reducerState = null,
			nextState -> validationState.reducerState = nextState
		);
	}

	/**
	 * Executes transition procedures and witness validators in a particle group and validates
	 * that the particle group is well formed.
	 */
	List<List<REStateUpdate>> statefulVerify(
		CMValidationState validationState,
		List<REInstruction> instructions
	) throws ConstraintMachineException {
		int instIndex = 0;
		var expectEnd = false;

		var groupedStateUpdates = new ArrayList<List<REStateUpdate>>();
		var stateUpdates = new ArrayList<REStateUpdate>();

		for (REInstruction inst : instructions) {
			validationState.curIndex = instIndex;
			validationState.curInstruction = inst;

			if (expectEnd && inst.getMicroOp() != REInstruction.REOp.END) {
				throw new ConstraintMachineException(CMErrorCode.MISSING_PARTICLE_GROUP, validationState);
			}

			if (inst.getMicroOp() == REInstruction.REOp.DOWNALL) {
				Class<? extends Particle> particleClass = inst.getData();
				var substateCursor = validationState.shutdownAll(particleClass);
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
						tmp.add(REStateUpdate.of(inst.getMicroOp(), substate, null, inst.getDataByteBuffer()));
						return substate.getParticle();
					}
				};
				try {
					callProcedure(validationState, inst.getMicroOp(), particleClass, iterator);
				} finally {
					substateCursor.close();
				}
			} else if (inst.isStateUpdate()) {
				final Particle nextParticle;
				final Substate substate;
				final byte[] arg;
				final Object o;
				if (inst.getMicroOp() == REInstruction.REOp.UP) {
					// TODO: Cleanup indexing of substate class
					substate = inst.getData();
					arg = null;
					nextParticle = substate.getParticle();
					o = nextParticle;
					validationState.bootUp(instIndex, substate);
				} else if (inst.getMicroOp() == REInstruction.REOp.VDOWN) {
					substate = inst.getData();
					arg = null;
					nextParticle = substate.getParticle();
					o = SubstateWithArg.noArg(nextParticle);
					validationState.virtualShutdown(substate);
				} else if (inst.getMicroOp() == REInstruction.REOp.VDOWNARG) {
					substate = (Substate) ((Pair) inst.getData()).getFirst();
					arg = (byte[]) ((Pair) inst.getData()).getSecond();
					nextParticle = substate.getParticle();
					o = SubstateWithArg.withArg(nextParticle, arg);
					validationState.virtualShutdown(substate);
				} else if (inst.getMicroOp() == com.radixdlt.constraintmachine.REInstruction.REOp.DOWN) {
					SubstateId substateId = inst.getData();
					nextParticle = validationState.shutdown(substateId);
					substate = Substate.create(nextParticle, substateId);
					arg = null;
					o = SubstateWithArg.noArg(nextParticle);
				} else if (inst.getMicroOp() == REInstruction.REOp.LDOWN) {
					SubstateId substateId = inst.getData();
					nextParticle = validationState.localShutdown(substateId.getIndex().orElseThrow());
					substate = Substate.create(nextParticle, substateId);
					arg = null;
					o = SubstateWithArg.noArg(nextParticle);
				} else {
					throw new ConstraintMachineException(CMErrorCode.UNKNOWN_OP, validationState);
				}

				stateUpdates.add(REStateUpdate.of(inst.getMicroOp(), substate, arg, inst.getDataByteBuffer()));

				callProcedure(validationState, inst.getMicroOp(), nextParticle.getClass(), o);

				expectEnd = validationState.reducerState == null;
			} else if (inst.getMicroOp() == com.radixdlt.constraintmachine.REInstruction.REOp.END) {
				groupedStateUpdates.add(stateUpdates);
				stateUpdates = new ArrayList<>();

				if (validationState.reducerState != null) {
					callProcedure(validationState, inst.getMicroOp(), null, null);
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
		CMStore cmStore,
		List<REInstruction> instructions,
		Optional<ECPublicKey> signature,
		PermissionLevel permissionLevel
	) throws TxnParseException, ConstraintMachineException {
		var validationState = new CMValidationState(
			virtualStoreLayer,
			dbTxn,
			cmStore,
			permissionLevel,
			signature
		);

		return this.statefulVerify(validationState, instructions);
	}
}
