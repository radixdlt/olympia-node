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

import com.radixdlt.DefaultSerialization;
import com.radixdlt.atom.Atom;
import com.radixdlt.atom.Substate;
import com.radixdlt.atom.SubstateId;
import com.radixdlt.atom.TxAction;
import com.radixdlt.atom.Txn;
import com.radixdlt.atommodel.tokens.TokenDefinitionParticle;
import com.radixdlt.atomos.Result;
import com.radixdlt.atomos.RriId;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.crypto.ECDSASignature;
import com.radixdlt.engine.RadixEngineErrorCode;
import com.radixdlt.engine.RadixEngineException;
import com.radixdlt.identifiers.RadixAddress;
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

		private Particle particleRemaining = null;
		private boolean particleRemainingIsInput;
		private ReducerState reducerState = null;

		private final Map<Integer, Particle> localUpParticles = new HashMap<>();
		private final Set<SubstateId> remoteDownParticles = new HashSet<>();
		private final HashCode witness;
		private final Optional<ECDSASignature> signature;
		private final CMStore store;
		private final CMStore.Transaction txn;
		private final Predicate<Particle> virtualStoreLayer;
		private RadixAddress signatureRequired;
		private TxAction txAction;

		CMValidationState(
			Predicate<Particle> virtualStoreLayer,
			CMStore.Transaction txn,
			CMStore store,
			PermissionLevel permissionLevel,
			HashCode witness,
			Optional<ECDSASignature> signature
		) {
			this.virtualStoreLayer = virtualStoreLayer;
			this.txn = txn;
			this.store = store;
			this.permissionLevel = permissionLevel;
			this.witness = witness;
			this.signature = signature;
		}

		public ImmutableIndex immutableIndex() {
			return (txn, rriId) ->
				localUpParticles.values().stream()
					.filter(TokenDefinitionParticle.class::isInstance)
					.map(TokenDefinitionParticle.class::cast)
					.filter(p -> RriId.fromRri(p.getRri()).equals(rriId))
					.findFirst()
					.map(Particle.class::cast)
					.or(() -> store.loadRriId(txn, rriId));
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
				return Optional.of(CMErrorCode.SPIN_CONFLICT);
			}

			if (!virtualStoreLayer.test(substate.getParticle())) {
				return Optional.of(CMErrorCode.INVALID_PARTICLE);
			}

			if (store.isVirtualDown(txn, substate.getId())) {
				return Optional.of(CMErrorCode.SPIN_CONFLICT);
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
			if (signature.isEmpty() || witness == null) {
				return false;
			}

			return publicKey.verify(witness, signature.get());
		}

		Particle getCurParticle() {
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

		void popAndReplace(Particle particle, boolean isInput, ReducerState reducerState) {
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
		Particle nextParticle,
		boolean isInput,
		boolean isRead
	) {
		final Particle curParticle = validationState.getCurParticle();

		if (validationState.spinClashes(isInput)) {
			return Optional.of(Pair.of(CMErrorCode.PARTICLE_REGISTER_SPIN_CLASH, null));
		}

		if (isRead && validationState.getReducerState() != null) {
			return Optional.of(Pair.of(CMErrorCode.PARTICLE_REGISTER_SPIN_CLASH, "Read clash"));
		}

		final Particle inputParticle = isInput ? nextParticle : curParticle;
		final Particle outputParticle = isInput ? curParticle : nextParticle;
		final TransitionToken transitionToken = new TransitionToken(
			inputParticle != null ? inputParticle.getClass() : VoidParticle.class,
			outputParticle != null ? outputParticle.getClass() : VoidParticle.class,
			isRead ? TypeToken.of(ReadOnlyData.class) : validationState.getReducerType()
		);

		final var transitionProcedure = this.particleProcedures.apply(transitionToken);
		if (inputParticle == null || outputParticle == null) {
			validationState.popAndReplace(nextParticle, isInput, isRead ? new ReadOnlyData() : null);
			return Optional.empty();
		}

		if (transitionProcedure == null) {
			return Optional.of(Pair.of(CMErrorCode.MISSING_TRANSITION_PROCEDURE, "TransitionToken{" + transitionToken + "}"));
		}

		final PermissionLevel requiredPermissionLevel = transitionProcedure.requiredPermissionLevel(inputParticle, outputParticle);
		if (validationState.permissionLevel.compareTo(requiredPermissionLevel) < 0) {
			return Optional.of(Pair.of(CMErrorCode.INVALID_EXECUTION_PERMISSION, null));
		}

		final var used = validationState.getReducerState();

		// Precondition check
		final Result preconditionCheckResult = transitionProcedure.precondition(
			inputParticle,
			outputParticle,
			used,
			validationState.immutableIndex()
		);
		if (preconditionCheckResult.isError()) {
			return Optional.of(Pair.of(CMErrorCode.TRANSITION_PRECONDITION_FAILURE, preconditionCheckResult.getErrorMessage()));
		}

		var reducer = transitionProcedure.inputOutputReducer();
		var result = reducer.reduce(inputParticle, outputParticle, validationState.immutableIndex(), used);
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

		var addressMaybe = transitionProcedure.inputSignatureRequired()
			.requiredSignature(inputParticle);
		if (addressMaybe.isPresent()) {
			var address = addressMaybe.get();
			if (validationState.signatureRequired != null) {
				if (!validationState.signatureRequired.equals(address)) {
					return Optional.of(Pair.of(CMErrorCode.TOO_MANY_REQUIRED_SIGNATURES, null));
				}
			} else {
				if (!validationState.verifySignedWith(address.getPublicKey())) {
					return Optional.of(Pair.of(CMErrorCode.INCORRECT_SIGNATURE, null));
				}

				validationState.signatureRequired = address;
			}
		}

		return Optional.empty();
	}

	private static class StatelessVerificationResult {
		private final List<REInstruction> instructions;
		private ECDSASignature signature;
		private HashCode hashToSign;

		StatelessVerificationResult() {
			this.instructions = new ArrayList<>();
		}

		void addParsed(REInstruction instruction) {
			this.instructions.add(instruction);
		}

		void hashToSign(HashCode hashToSign) {
			this.hashToSign = hashToSign;
		}

		void signature(ECDSASignature signature) {
			this.signature = signature;
		}

	}

	StatelessVerificationResult statelessVerify(Txn txn) throws RadixEngineException {
		final Atom atom;
		try {
			atom = DefaultSerialization.getInstance().fromDson(txn.getPayload(), Atom.class);
		} catch (DeserializeException e) {
			throw new RadixEngineException(txn, RadixEngineErrorCode.TXN_ERROR, "Cannot deserialize txn");
		}

		var statelessVerification = new StatelessVerificationResult();

		long particleIndex = 0;
		int instIndex = 0;
		int numMessages = 0;

		var buf = ByteBuffer.wrap(atom.getUnsignedBlob());
		while (buf.hasRemaining()) {
			final REInstruction inst;
			try {
				inst = REInstruction.readFrom(txn, instIndex, buf);
			} catch (DeserializeException e) {
				throw new RadixEngineException(txn, RadixEngineErrorCode.TXN_ERROR, instIndex + ": Could not read instruction");
			}
			statelessVerification.addParsed(inst);

			if (inst.hasSubstate()) {
				var data = inst.getData();
				if (data instanceof Substate) {
					Substate substate = (Substate) data;
					final Result staticCheckResult = particleStaticCheck.apply(substate.getParticle());
					if (staticCheckResult.isError()) {
						var errMsg = staticCheckResult.getErrorMessage();
						throw new RadixEngineException(txn, RadixEngineErrorCode.TXN_ERROR, instIndex + ": " + errMsg);
					}
				}
				particleIndex++;

			} else if (inst.getMicroOp() == REInstruction.REOp.MSG) {
				numMessages++;
				if (numMessages > MAX_NUM_MESSAGES) {
					throw new RadixEngineException(txn, RadixEngineErrorCode.TXN_ERROR, instIndex + ": Too many messages.");
				}
			} else if (inst.getMicroOp() == com.radixdlt.constraintmachine.REInstruction.REOp.END) {
				if (particleIndex == 0) {
					throw new RadixEngineException(txn, RadixEngineErrorCode.TXN_ERROR, instIndex + ": Empty group.");
				}

				particleIndex = 0;
			} else {
				throw new RadixEngineException(txn, RadixEngineErrorCode.TXN_ERROR,
					instIndex + ": Unknown CM Operation " + inst.getMicroOp());
			}

			instIndex++;
		}

		if (particleIndex != 0) {
			throw new RadixEngineException(txn, RadixEngineErrorCode.TXN_ERROR, instIndex + ": Missing group");
		}

		var hashToSign = atom.computeHashToSign();
		statelessVerification.hashToSign(hashToSign);
		atom.getSignature().ifPresent(statelessVerification::signature);

		return statelessVerification;
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
				if (inst.getMicroOp() == REInstruction.REOp.UP) {
					// TODO: Cleanup indexing of substate class
					substate = inst.getData();
					nextParticle = substate.getParticle();
					validationState.bootUp(instIndex, substate);
				} else if (inst.getMicroOp() == REInstruction.REOp.VDOWN) {
					substate = inst.getData();
					nextParticle = substate.getParticle();
					var stateError = validationState.virtualShutdown(substate);
					if (stateError.isPresent()) {
						return Optional.of(new CMError(instIndex, stateError.get(), validationState));
					}
				} else if (inst.getMicroOp() == com.radixdlt.constraintmachine.REInstruction.REOp.DOWN) {
					SubstateId substateId = inst.getData();
					var maybeParticle = validationState.shutdown(substateId);
					if (maybeParticle.isEmpty()) {
						return Optional.of(new CMError(instIndex, CMErrorCode.SPIN_CONFLICT, validationState));
					}
					nextParticle = maybeParticle.get();
					substate = Substate.create(nextParticle, substateId);
				} else if (inst.getMicroOp() == REInstruction.REOp.LDOWN) {
					SubstateId substateId = inst.getData();
					var maybeParticle = validationState.localShutdown(substateId.getIndex().orElseThrow());
					if (maybeParticle.isEmpty()) {
						return Optional.of(new CMError(instIndex, CMErrorCode.LOCAL_NONEXISTENT, validationState));
					}
					nextParticle = maybeParticle.get();
					substate = Substate.create(nextParticle, substateId);
				} else if (inst.getMicroOp() == REInstruction.REOp.READ) {
					var substateId = SubstateId.fromBuffer(inst.getData());
					var maybeParticle = validationState.read(substateId);
					if (maybeParticle.isEmpty()) {
						return Optional.of(new CMError(instIndex, CMErrorCode.READ_FAILURE, validationState));
					}
					nextParticle = maybeParticle.get();
					substate = Substate.create(nextParticle, substateId);
				} else if (inst.getMicroOp() == REInstruction.REOp.LREAD) {
					SubstateId substateId = inst.getData();
					var maybeParticle = validationState.localRead(substateId.getIndex().orElseThrow());
					if (maybeParticle.isEmpty()) {
						return Optional.of(new CMError(instIndex, CMErrorCode.LOCAL_NONEXISTENT, validationState));
					}
					nextParticle = maybeParticle.get();
					substate = Substate.create(nextParticle, substateId);
				} else {
					return Optional.of(new CMError(instIndex, CMErrorCode.UNKNOWN_OP, validationState));
				}

				var error = validateParticle(
					validationState,
					nextParticle,
					inst.getCheckSpin() == Spin.UP,
					!inst.isPush() && inst.getCheckSpin() == Spin.UP
				);
				if (error.isPresent()) {
					return Optional.of(new CMError(instIndex, error.get().getFirst(), validationState, error.get().getSecond()));
				}

				parsedInstructions.add(REParsedInstruction.of(inst, substate));
			} else if (inst.getMicroOp() == com.radixdlt.constraintmachine.REInstruction.REOp.END) {
				final Pair<Particle, ReducerState> deallocated;
				if (validationState.txAction == null && validationState.particleRemainingIsInput) {
					var particle = validationState.particleRemaining;
					var reducerState = validationState.reducerState;
					var errMaybe = validateParticle(validationState,
						VoidParticle.create(), false, false);
					if (errMaybe.isPresent()) {
						return Optional.of(new CMError(instIndex,
							errMaybe.get().getFirst(), validationState, errMaybe.get().getSecond()));
					}
					deallocated = Pair.of(particle, reducerState);
				} else {
					deallocated = null;
				}

				if (validationState.txAction == null) {
					return Optional.of(new CMError(instIndex, CMErrorCode.UNEQUAL_INPUT_OUTPUT, validationState));
				}

				var parsedAction = REParsedAction.create(validationState.txAction, parsedInstructions, deallocated);
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
	public REParsedTxn validate(
		CMStore.Transaction dbTxn,
		CMStore cmStore,
		Txn txn,
		PermissionLevel permissionLevel
	) throws RadixEngineException {
		var result = this.statelessVerify(txn);

		final CMValidationState validationState = new CMValidationState(
			virtualStoreLayer,
			dbTxn,
			cmStore,
			permissionLevel,
			result.hashToSign,
			Optional.ofNullable(result.signature)
		);

		var parsedActions = new ArrayList<REParsedAction>();
		var error = this.statefulVerify(validationState, result.instructions, parsedActions);
		if (error.isPresent()) {
			throw new RadixEngineException(txn, RadixEngineErrorCode.CM_ERROR, error.get().getErrorDescription(), error.get());
		}

		var address = validationState.signatureRequired;

		return new REParsedTxn(txn, address, parsedActions);
	}
}
