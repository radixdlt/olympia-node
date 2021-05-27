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

import com.google.common.hash.HashCode;

import com.radixdlt.atom.Substate;
import com.radixdlt.atom.SubstateCursor;
import com.radixdlt.atom.SubstateId;
import com.radixdlt.atom.TxAction;
import com.radixdlt.atom.Txn;
import com.radixdlt.atommodel.system.state.SystemParticle;
import com.radixdlt.atommodel.tokens.state.TokenDefinitionParticle;
import com.radixdlt.atomos.Result;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.crypto.ECDSASignature;
import com.radixdlt.crypto.HashUtils;
import com.radixdlt.serialization.DeserializeException;
import com.radixdlt.store.CMStore;
import com.radixdlt.store.ReadableAddrs;
import com.radixdlt.utils.Pair;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * An implementation of a UTXO based constraint machine which uses Radix's atom structure.
 */
// FIXME: unchecked, rawtypes
@SuppressWarnings({"unchecked", "rawtypes"})
public final class ConstraintMachine {
	private static final int MAX_TXN_SIZE = 1024 * 1024;
	private static final int MAX_NUM_MESSAGES = 1;

	public static class Builder {
		private Predicate<Particle> virtualStoreLayer;
		private Function<Particle, Result> particleStaticCheck;
		private Procedures procedures;

		public Builder setParticleStaticCheck(Function<Particle, Result> particleStaticCheck) {
			this.particleStaticCheck = particleStaticCheck;
			return this;
		}

		public Builder setParticleTransitionProcedures(Procedures procedures) {
			this.procedures = procedures;
			return this;
		}

		public Builder setVirtualStoreLayer(Predicate<Particle> virtualStoreLayer) {
			this.virtualStoreLayer = virtualStoreLayer;
			return this;
		}

		public ConstraintMachine build() {
			return new ConstraintMachine(
				virtualStoreLayer,
				particleStaticCheck,
				procedures
			);
		}
	}

	private final Predicate<Particle> virtualStoreLayer;
	private final Function<Particle, Result> particleStaticCheck;
	private final Procedures procedures;

	ConstraintMachine(
		Predicate<Particle> virtualStoreLayer,
		Function<Particle, Result> particleStaticCheck,
		Procedures procedures
	) {
		this.virtualStoreLayer = virtualStoreLayer;
		this.particleStaticCheck = particleStaticCheck;
		this.procedures = procedures;
	}

	public static final class CMValidationState {
		private PermissionLevel permissionLevel;
		private REInstruction curInstruction;
		private int curIndex;

		private ReducerState reducerState = null;

		private final Map<Integer, Particle> localUpParticles = new HashMap<>();
		private final Set<SubstateId> remoteDownParticles = new HashSet<>();
		private final Optional<ECPublicKey> signedBy;
		private final CMStore store;
		private final CMStore.Transaction txn;
		private final Predicate<Particle> virtualStoreLayer;
		private TxAction txAction;

		CMValidationState(
			Predicate<Particle> virtualStoreLayer,
			CMStore.Transaction txn,
			CMStore store,
			PermissionLevel permissionLevel,
			Optional<ECPublicKey> signedBy
		) {
			this.virtualStoreLayer = virtualStoreLayer;
			this.txn = txn;
			this.store = store;
			this.permissionLevel = permissionLevel;
			this.signedBy = signedBy;
		}

		public ReadableAddrs immutableIndex() {
			// TODO: Fix ReadableAddrs interface (remove txn)
			return (ignoredTxn, addr) -> {
				if (addr.isSystem()) {
					return localUpParticles.values().stream()
						.filter(SystemParticle.class::isInstance)
						.findFirst()
						.or(() -> store.loadAddr(txn, addr))
						.or(() -> Optional.of(new SystemParticle(0, 0, 0))); // A bit of a hack
				} else {
					return localUpParticles.values().stream()
						.filter(TokenDefinitionParticle.class::isInstance)
						.map(TokenDefinitionParticle.class::cast)
						.filter(p -> p.getAddr().equals(addr))
						.findFirst()
						.map(Particle.class::cast)
						.or(() -> store.loadAddr(txn, addr));
				}
			};
		}

		public Optional<Particle> loadUpParticle(SubstateId substateId) {
			if (remoteDownParticles.contains(substateId)) {
				return Optional.empty();
			}

			return store.loadUpParticle(txn, substateId);
		}

		public void bootUp(int instructionIndex, Substate substate) {
			localUpParticles.put(instructionIndex, substate.getParticle());
		}

		public void virtualShutdown(Substate substate) throws ConstraintMachineException {
			if (remoteDownParticles.contains(substate.getId())) {
				throw new ConstraintMachineException(CMErrorCode.SUBSTATE_NOT_FOUND, this);
			}

			if (!virtualStoreLayer.test(substate.getParticle())) {
				throw new ConstraintMachineException(CMErrorCode.INVALID_PARTICLE, this);
			}

			if (store.isVirtualDown(txn, substate.getId())) {
				throw new ConstraintMachineException(CMErrorCode.SUBSTATE_NOT_FOUND, this);
			}

			remoteDownParticles.add(substate.getId());
		}

		public Particle localShutdown(int index) throws ConstraintMachineException {
			var maybeParticle = localUpParticles.remove(index);
			if (maybeParticle == null) {
				throw new ConstraintMachineException(CMErrorCode.LOCAL_NONEXISTENT, this);
			}

			return maybeParticle;
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

		public SubstateCursor shutdownAll(Class<? extends Particle> particleClass) throws ConstraintMachineException {
			// TODO: add to remoteDownParticles?
			return store.openIndexedCursor(txn, particleClass);
		}

		Class<? extends ReducerState> getReducerStateClass() {
			return reducerState != null ? reducerState.getClass() : VoidReducerState.class;
		}

		@Override
		public String toString() {
			return String.format("CMState{state=%s inst=%s}", this.reducerState, this.curInstruction);
		}
	}


	public static class ParseResult {
		private final List<REInstruction> instructions;
		private ECDSASignature signature;
		private HashCode hashToSign;
		private ECPublicKey publicKey;
		private final Txn txn;
		private byte[] msg;

		ParseResult(Txn txn) {
			this.txn = txn;
			this.instructions = new ArrayList<>();
		}

		void msg(byte[] msg) {
			this.msg = msg;
		}

		public Optional<byte[]> getMsg() {
			return Optional.ofNullable(msg);
		}

		void addParsed(REInstruction instruction) {
			this.instructions.add(instruction);
		}

		void signatureData(HashCode hashToSign, ECDSASignature signature, ECPublicKey publicKey) {
			this.hashToSign = hashToSign;
			this.signature = signature;
			this.publicKey = publicKey;
		}

		public List<REInstruction> instructionsParsed() {
			return instructions;
		}

		public Optional<ECPublicKey> getSignedBy() {
			return Optional.ofNullable(publicKey);
		}

		private int index() {
			return instructions.size();
		}

		public TxnParseException exception(String message) {
			return new TxnParseException(
				message + "@" + index() + " parsed: " + instructions,
				this
			);
		}
	}

	public ParseResult parse(Txn txn) throws TxnParseException {
		var verifierState = new ParseResult(txn);

		if (txn.getPayload().length > MAX_TXN_SIZE) {
			throw verifierState.exception("Transaction is too big: " + txn.getPayload().length + " > " + MAX_TXN_SIZE);
		}

		long particleIndex = 0;
		int numMessages = 0;
		ECDSASignature sig = null;
		int sigPosition = 0;

		var buf = ByteBuffer.wrap(txn.getPayload());
		while (buf.hasRemaining()) {
			if (sig != null) {
				throw verifierState.exception("Signature must be last");
			}

			int curPos = buf.position();
			final REInstruction inst;
			try {
				inst = REInstruction.readFrom(txn, verifierState.index(), buf);
			} catch (DeserializeException e) {
				throw verifierState.exception("Could not read instruction");
			}
			verifierState.addParsed(inst);

			if (inst.isStateUpdate()) {
				var data = inst.getData();
				if (data instanceof Substate) {
					Substate substate = (Substate) data;
					final Result staticCheckResult = particleStaticCheck.apply(substate.getParticle());
					if (staticCheckResult.isError()) {
						var errMsg = staticCheckResult.getErrorMessage();
						throw verifierState.exception(errMsg);
					}
				} else if (data instanceof Pair) {
					Substate substate = (Substate) ((Pair) data).getFirst();
					final Result staticCheckResult = particleStaticCheck.apply(substate.getParticle());
					if (staticCheckResult.isError()) {
						var errMsg = staticCheckResult.getErrorMessage();
						throw verifierState.exception(errMsg);
					}
				}

				particleIndex++;

			} else if (inst.getMicroOp() == REInstruction.REOp.MSG) {
				numMessages++;
				if (numMessages > MAX_NUM_MESSAGES) {
					throw verifierState.exception("Too many messages");
				}
				verifierState.msg(inst.getData());
			} else if (inst.getMicroOp() == REInstruction.REOp.END) {
				if (particleIndex == 0) {
					throw verifierState.exception("Empty group");
				}
				particleIndex = 0;
			} else if (inst.getMicroOp() == REInstruction.REOp.SIG) {
				sigPosition = curPos;
				sig = inst.getData();
			} else {
				throw verifierState.exception("Unknown CM Op " + inst.getMicroOp());
			}
		}

		if (particleIndex != 0) {
			throw verifierState.exception("Missing group");
		}

		if (sig != null) {
			var hash = HashUtils.sha256(txn.getPayload(), 0, sigPosition); // This is a double hash
			var pubKey = ECPublicKey.recoverFrom(hash, sig)
				.orElseThrow(() -> verifierState.exception("Invalid signature"));
			if (!pubKey.verify(hash, sig)) {
				throw verifierState.exception("Invalid signature");
			}
			verifierState.signatureData(hash, sig, pubKey);
		}

		return verifierState;
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
			if (validationState.permissionLevel.compareTo(requiredLevel) < 0) {
				throw new ConstraintMachineException(
					CMErrorCode.PERMISSION_LEVEL_ERROR,
					validationState,
					"Required: " + requiredLevel + " Current: " + validationState.permissionLevel
				);
			}
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
			txAction -> {
				validationState.reducerState = null;
				validationState.txAction = txAction.orElse(null);
			},
			(nextState, txAction) -> {
				validationState.reducerState = nextState;
				validationState.txAction = txAction.orElse(null);
			}
		);
	}

	/**
	 * Executes transition procedures and witness validators in a particle group and validates
	 * that the particle group is well formed.
	 *
	 * @return the first error found, otherwise an empty optional
	 */
	void statefulVerify(
		CMValidationState validationState,
		List<REInstruction> instructions,
		List<List<REStateUpdate>> parsedInstructions,
		List<REParsedAction> parsedActions
	) throws ConstraintMachineException {
		int instIndex = 0;
		var expectEnd = false;

		var parsed = new ArrayList<REStateUpdate>();

		for (REInstruction inst : instructions) {
			validationState.curIndex = instIndex;
			validationState.curInstruction = inst;

			if (expectEnd && inst.getMicroOp() != REInstruction.REOp.END) {
				throw new ConstraintMachineException(CMErrorCode.MISSING_PARTICLE_GROUP, validationState);
			}

			if (inst.getMicroOp() == REInstruction.REOp.DOWNALL) {
				Class<? extends Particle> particleClass = inst.getData();
				var substateCursor = validationState.shutdownAll(particleClass);
				final var stateUpdates = parsed;
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
						stateUpdates.add(REStateUpdate.of(inst.getMicroOp(), substate, inst.getDataByteBuffer()));
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
				final Object o;
				if (inst.getMicroOp() == REInstruction.REOp.UP) {
					// TODO: Cleanup indexing of substate class
					substate = inst.getData();
					nextParticle = substate.getParticle();
					o = nextParticle;
					validationState.bootUp(instIndex, substate);
				} else if (inst.getMicroOp() == REInstruction.REOp.VDOWN) {
					substate = inst.getData();
					nextParticle = substate.getParticle();
					o = SubstateWithArg.noArg(nextParticle);
					validationState.virtualShutdown(substate);
				} else if (inst.getMicroOp() == REInstruction.REOp.VDOWNARG) {
					substate = (Substate) ((Pair) inst.getData()).getFirst();
					var argument = (byte[]) ((Pair) inst.getData()).getSecond();
					nextParticle = substate.getParticle();
					o = SubstateWithArg.withArg(nextParticle, argument);
					validationState.virtualShutdown(substate);
				} else if (inst.getMicroOp() == com.radixdlt.constraintmachine.REInstruction.REOp.DOWN) {
					SubstateId substateId = inst.getData();
					nextParticle = validationState.shutdown(substateId);
					substate = Substate.create(nextParticle, substateId);
					o = SubstateWithArg.noArg(nextParticle);
				} else if (inst.getMicroOp() == REInstruction.REOp.LDOWN) {
					SubstateId substateId = inst.getData();
					nextParticle = validationState.localShutdown(substateId.getIndex().orElseThrow());
					substate = Substate.create(nextParticle, substateId);
					o = SubstateWithArg.noArg(nextParticle);
				} else {
					throw new ConstraintMachineException(CMErrorCode.UNKNOWN_OP, validationState);
				}

				parsed.add(REStateUpdate.of(inst.getMicroOp(), substate, inst.getDataByteBuffer()));

				callProcedure(validationState, inst.getMicroOp(), nextParticle.getClass(), o);

				expectEnd = validationState.reducerState == null;
			} else if (inst.getMicroOp() == com.radixdlt.constraintmachine.REInstruction.REOp.END) {
				parsedInstructions.add(parsed);
				parsed = new ArrayList<>();

				if (validationState.reducerState != null) {
					callProcedure(validationState, inst.getMicroOp(), null, null);
				}

				expectEnd = false;
			}

			if (validationState.txAction != null) {
				var parsedAction = REParsedAction.create(validationState.txAction);
				parsedActions.add(parsedAction);
				validationState.txAction = null;
			}

			instIndex++;
		}
	}

	/**
	 * Validates a CM instruction and calculates the necessary state checks and post-validation
	 * write logic.
	 *
	 * @return the first error found, otherwise an empty optional
	 */
	public REParsedTxn verify(
		CMStore.Transaction dbTxn,
		CMStore cmStore,
		Txn txn,
		PermissionLevel permissionLevel
	) throws TxnParseException, ConstraintMachineException {
		var result = this.parse(txn);
		var validationState = new CMValidationState(
			virtualStoreLayer,
			dbTxn,
			cmStore,
			permissionLevel,
			Optional.ofNullable(result.publicKey)
		);
		var parsedActions = new ArrayList<REParsedAction>();
		var parsedInstructions = new ArrayList<List<REStateUpdate>>();
		this.statefulVerify(validationState, result.instructions, parsedInstructions, parsedActions);

		return new REParsedTxn(txn, result, parsedInstructions,  parsedActions);
	}
}
