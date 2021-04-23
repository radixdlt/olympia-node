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
import com.google.common.reflect.TypeToken;

import com.radixdlt.atom.Substate;
import com.radixdlt.atom.SubstateId;
import com.radixdlt.atom.TxAction;
import com.radixdlt.atom.Txn;
import com.radixdlt.atommodel.tokens.TokenDefinitionParticle;
import com.radixdlt.atomos.Result;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.crypto.ECDSASignature;
import com.radixdlt.crypto.HashUtils;
import com.radixdlt.engine.RadixEngineErrorCode;
import com.radixdlt.engine.RadixEngineException;
import com.radixdlt.serialization.DeserializeException;
import com.radixdlt.store.CMStore;
import com.radixdlt.store.ImmutableIndex;
import com.radixdlt.utils.Pair;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
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
		private Function<TransitionToken, TransitionProcedure<Particle, Particle, ReducerState>> particleProcedures;

		public Builder setParticleStaticCheck(Function<Particle, Result> particleStaticCheck) {
			this.particleStaticCheck = particleStaticCheck;
			return this;
		}

		public Builder setParticleTransitionProcedures(
			Function<TransitionToken, TransitionProcedure<Particle, Particle, ReducerState>> particleProcedures
		) {
			this.particleProcedures = particleProcedures;
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
				particleProcedures
			);
		}
	}

	private final Predicate<Particle> virtualStoreLayer;
	private final Function<Particle, Result> particleStaticCheck;
	private final Function<TransitionToken, TransitionProcedure<Particle, Particle, ReducerState>> particleProcedures;

	ConstraintMachine(
		Predicate<Particle> virtualStoreLayer,
		Function<Particle, Result> particleStaticCheck,
		Function<TransitionToken, TransitionProcedure<Particle, Particle, ReducerState>> particleProcedures
	) {
		this.virtualStoreLayer = virtualStoreLayer;
		this.particleStaticCheck = particleStaticCheck;
		this.particleProcedures = particleProcedures;
	}

	public static final class CMValidationState {
		private PermissionLevel permissionLevel;

		private SubstateWithArg<Particle> particleRemaining = null;
		private boolean particleRemainingIsInput;
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

		public ImmutableIndex immutableIndex() {
			return (txn, rri) ->
				localUpParticles.values().stream()
					.filter(TokenDefinitionParticle.class::isInstance)
					.map(TokenDefinitionParticle.class::cast)
					.filter(p -> p.getAddr().equals(rri))
					.findFirst()
					.map(Particle.class::cast)
					.or(() -> store.loadRri(txn, rri));
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

		public Optional<CMErrorCode> virtualShutdown(Substate substate) {
			if (remoteDownParticles.contains(substate.getId())) {
				return Optional.of(CMErrorCode.SUBSTATE_NOT_FOUND);
			}

			if (!virtualStoreLayer.test(substate.getParticle())) {
				return Optional.of(CMErrorCode.INVALID_PARTICLE);
			}

			if (store.isVirtualDown(txn, substate.getId())) {
				return Optional.of(CMErrorCode.SUBSTATE_NOT_FOUND);
			}

			remoteDownParticles.add(substate.getId());
			return Optional.empty();
		}

		public Optional<Particle> localShutdown(int index) {
			var maybeParticle = localUpParticles.remove(index);
			return Optional.ofNullable(maybeParticle);
		}

		public Optional<Particle> localRead(int index) {
			var maybeParticle = localUpParticles.get(index);
			return Optional.ofNullable(maybeParticle);
		}

		public Optional<Particle> read(SubstateId substateId) {
			return loadUpParticle(substateId);
		}

		public Optional<Particle> shutdown(SubstateId substateId) {
			var maybeParticle = loadUpParticle(substateId);
			remoteDownParticles.add(substateId);
			return maybeParticle;
		}

		private boolean verifySignedWith(ECPublicKey publicKey) {
			return signedBy.map(publicKey::equals).orElse(false);
		}

		SubstateWithArg<Particle> getCurParticle() {
			return particleRemaining;
		}

		boolean spinClashes(boolean nextIsInput) {
			return particleRemaining != null && nextIsInput == particleRemainingIsInput;
		}

		TypeToken<? extends ReducerState> getReducerType() {
			return particleRemaining != null && reducerState != null
				? reducerState.getTypeToken() : TypeToken.of(VoidReducerState.class);
		}

		ReducerState getReducerState() {
			return particleRemaining != null ? reducerState : null;
		}

		void popAndComplete(TxAction txAction) {
			this.particleRemaining = null;
			this.reducerState = null;
			this.txAction = txAction;
		}

		void popAndReplace(SubstateWithArg<Particle> particle, boolean isInput, ReducerState reducerState) {
			this.particleRemaining = particle;
			this.particleRemainingIsInput = isInput;
			this.reducerState = reducerState;
		}

		void updateState(ReducerState reducerState) {
			this.reducerState = reducerState;
		}

		@Override
		public String toString() {
			StringBuilder builder = new StringBuilder();
			builder.append("CMTrace:[");
			if (particleRemaining != null) {
				builder
					.append(" Remaining (")
					.append(this.particleRemainingIsInput ? "input" : "output")
					.append("): ")
					.append(this.particleRemaining)
					.append(" Used: ")
					.append(this.reducerState);
			} else {
				builder.append(" Remaining: [empty] ");
			}
			builder.append(" ]");

			return builder.toString();
		}
	}

	/**
	 * Executes a transition procedure given the next spun particle and a current validation state.
	 *
	 * @param validationState local state of validation
	 * @return the first error found, otherwise an empty optional
	 */
	Optional<Pair<CMErrorCode, String>> validateParticle(
		CMValidationState validationState,
		SubstateWithArg<Particle> nextParticle,
		boolean isInput
	) {
		final SubstateWithArg<Particle> curParticle = validationState.getCurParticle();

		if (validationState.spinClashes(isInput)) {
			return Optional.of(Pair.of(CMErrorCode.PARTICLE_REGISTER_SPIN_CLASH, null));
		}

		final SubstateWithArg<Particle> input = isInput ? nextParticle : curParticle;
		final Particle outputParticle = isInput ? (curParticle == null ? null : curParticle.getSubstate())
			: nextParticle.getSubstate();
		final TransitionToken transitionToken = new TransitionToken(
			input != null ? input.getSubstate().getClass() : VoidParticle.class,
			outputParticle != null ? outputParticle.getClass() : VoidParticle.class,
			validationState.getReducerType()
		);

		final var transitionProcedure = this.particleProcedures.apply(transitionToken);
		if (input == null || outputParticle == null) {
			validationState.popAndReplace(nextParticle, isInput, null);
			return Optional.empty();
		}

		if (transitionProcedure == null) {
			return Optional.of(Pair.of(CMErrorCode.MISSING_TRANSITION_PROCEDURE, "TransitionToken{" + transitionToken + "}"));
		}

		final var used = validationState.getReducerState();

		// Precondition check
		final Result preconditionCheckResult = transitionProcedure.precondition(
			input,
			outputParticle,
			used,
			validationState.immutableIndex()
		);
		if (preconditionCheckResult.isError()) {
			return Optional.of(Pair.of(CMErrorCode.TRANSITION_PRECONDITION_FAILURE, preconditionCheckResult.getErrorMessage()));
		}

		final var requiredPermissionLevel = transitionProcedure.requiredPermissionLevel(
			input, outputParticle, validationState.immutableIndex()
		);
		if (validationState.permissionLevel.compareTo(requiredPermissionLevel) < 0) {
			return Optional.of(Pair.of(CMErrorCode.INVALID_EXECUTION_PERMISSION, null));
		}

		var reducer = transitionProcedure.inputOutputReducer();
		var result = reducer.reduce(input, outputParticle, validationState.immutableIndex(), used);
		result.ifIncompleteElse(
			(keepInput, state) -> {
				if (isInput == keepInput) {
					validationState.popAndReplace(nextParticle, isInput, state);
				} else {
					validationState.updateState(state);
				}
			},
			validationState::popAndComplete
		);

		// System permissions don't require signatures
		if (validationState.permissionLevel == PermissionLevel.SYSTEM) {
			return Optional.empty();
		}

		var signatureVerified = transitionProcedure.signatureValidator()
			.verify(input, outputParticle, validationState.immutableIndex(), validationState.signedBy);
		if (!signatureVerified) {
			return Optional.of(Pair.of(CMErrorCode.INCORRECT_SIGNATURE, null));
		}

		return Optional.empty();
	}

	public static class StatelessVerificationResult {
		private final List<REInstruction> instructions;
		private ECDSASignature signature;
		private HashCode hashToSign;
		private ECPublicKey publicKey;
		private final Txn txn;

		StatelessVerificationResult(Txn txn) {
			this.txn = txn;
			this.instructions = new ArrayList<>();
		}

		void addParsed(REInstruction instruction) {
			this.instructions.add(instruction);
		}

		void signatureData(HashCode hashToSign, ECDSASignature signature, ECPublicKey publicKey) {
			this.hashToSign = hashToSign;
			this.signature = signature;
			this.publicKey = publicKey;
		}

		public Optional<ECPublicKey> getRecovered() {
			return Optional.ofNullable(publicKey);
		}

		private int index() {
			return instructions.size();
		}

		public RadixEngineException exception(String message) {
			return new RadixEngineException(txn, RadixEngineErrorCode.TXN_ERROR, message + "@" + index() + " parsed: " + instructions);
		}
	}

	public StatelessVerificationResult statelessVerify(Txn txn) throws RadixEngineException {
		var verifierState = new StatelessVerificationResult(txn);

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

			if (inst.hasSubstate()) {
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
				.orElseThrow(() -> new RadixEngineException(txn, RadixEngineErrorCode.TXN_ERROR, "Invalid signature"));
			if (!pubKey.verify(hash, sig)) {
				throw verifierState.exception("Invalid signature");
			}
			verifierState.signatureData(hash, sig, pubKey);
		}

		return verifierState;
	}

	/**
	 * Executes transition procedures and witness validators in a particle group and validates
	 * that the particle group is well formed.
	 *
	 * @return the first error found, otherwise an empty optional
	 */
	Optional<CMError> statefulVerify(
		CMValidationState validationState,
		List<REInstruction> instructions,
		List<REParsedAction> parsedActions
	) {
		var parsedInstructions = new ArrayList<REParsedInstruction>();
		int instIndex = 0;
		var expectEnd = false;

		for (REInstruction inst : instructions) {
			if (expectEnd && inst.getMicroOp() != REInstruction.REOp.END) {
				return Optional.of(new CMError(instIndex, CMErrorCode.MISSING_PARTICLE_GROUP, validationState));
			}

			if (inst.hasSubstate()) {
				final Particle nextParticle;
				final Substate substate;
				final byte[] argument;
				if (inst.getMicroOp() == REInstruction.REOp.UP) {
					// TODO: Cleanup indexing of substate class
					substate = inst.getData();
					nextParticle = substate.getParticle();
					validationState.bootUp(instIndex, substate);
					argument = null;
				} else if (inst.getMicroOp() == REInstruction.REOp.VDOWN) {
					substate = inst.getData();
					argument = null;
					nextParticle = substate.getParticle();
					var stateError = validationState.virtualShutdown(substate);
					if (stateError.isPresent()) {
						return Optional.of(new CMError(instIndex, stateError.get(), validationState));
					}
				} else if (inst.getMicroOp() == REInstruction.REOp.VDOWNARG) {
					substate = (Substate) ((Pair) inst.getData()).getFirst();
					argument = (byte[]) ((Pair) inst.getData()).getSecond();
					nextParticle = substate.getParticle();
					var stateError = validationState.virtualShutdown(substate);
					if (stateError.isPresent()) {
						return Optional.of(new CMError(instIndex, stateError.get(), validationState));
					}
				} else if (inst.getMicroOp() == com.radixdlt.constraintmachine.REInstruction.REOp.DOWN) {
					SubstateId substateId = inst.getData();
					var maybeParticle = validationState.shutdown(substateId);
					if (maybeParticle.isEmpty()) {
						return Optional.of(new CMError(instIndex, CMErrorCode.SUBSTATE_NOT_FOUND, validationState));
					}
					nextParticle = maybeParticle.get();
					substate = Substate.create(nextParticle, substateId);
					argument = null;
				} else if (inst.getMicroOp() == REInstruction.REOp.LDOWN) {
					SubstateId substateId = inst.getData();
					var maybeParticle = validationState.localShutdown(substateId.getIndex().orElseThrow());
					if (maybeParticle.isEmpty()) {
						return Optional.of(new CMError(instIndex, CMErrorCode.LOCAL_NONEXISTENT, validationState));
					}
					nextParticle = maybeParticle.get();
					substate = Substate.create(nextParticle, substateId);
					argument = null;
				} else {
					return Optional.of(new CMError(instIndex, CMErrorCode.UNKNOWN_OP, validationState));
				}

				var error = validateParticle(
					validationState,
					argument == null ? SubstateWithArg.noArg(nextParticle)
						: SubstateWithArg.withArg(nextParticle, argument),
					inst.getCheckSpin() == Spin.UP
				);
				if (error.isPresent()) {
					return Optional.of(new CMError(instIndex, error.get().getFirst(), validationState, error.get().getSecond()));
				}

				parsedInstructions.add(REParsedInstruction.of(inst, substate));
			} else if (inst.getMicroOp() == com.radixdlt.constraintmachine.REInstruction.REOp.END) {
				if (validationState.txAction == null) {
					var errMaybe = validateParticle(validationState,
						SubstateWithArg.noArg(VoidParticle.create()), !validationState.particleRemainingIsInput);
					if (errMaybe.isPresent()) {
						return Optional.of(new CMError(instIndex,
							errMaybe.get().getFirst(), validationState, errMaybe.get().getSecond()));
					}
				}

				if (validationState.txAction == null) {
					return Optional.of(new CMError(instIndex, CMErrorCode.UNEQUAL_INPUT_OUTPUT, validationState));
				}

				var parsedAction = REParsedAction.create(validationState.txAction, parsedInstructions);
				parsedActions.add(parsedAction);
				parsedInstructions = new ArrayList<>();
				validationState.txAction = null;
			}

			expectEnd = validationState.txAction != null;
			instIndex++;
		}

		return Optional.empty();
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
	) throws RadixEngineException {
		var result = this.statelessVerify(txn);
		var validationState = new CMValidationState(
			virtualStoreLayer,
			dbTxn,
			cmStore,
			permissionLevel,
			Optional.ofNullable(result.publicKey)
		);

		var parsedActions = new ArrayList<REParsedAction>();
		var error = this.statefulVerify(validationState, result.instructions, parsedActions);
		if (error.isPresent()) {
			throw new RadixEngineException(txn, RadixEngineErrorCode.CM_ERROR, error.get().getErrorDescription(), error.get());
		}

		var signedBy = validationState.signedBy;
		return new REParsedTxn(txn, signedBy, parsedActions);
	}
}
