package com.radixdlt.engine;

import com.radixdlt.atoms.DataPointer;
import com.radixdlt.atoms.ImmutableAtom;
import com.radixdlt.atoms.Particle;
import com.radixdlt.atoms.Spin;
import com.radixdlt.atoms.SpunParticle;
import com.radixdlt.common.Pair;
import com.radixdlt.constraintmachine.CMAtom;
import com.radixdlt.constraintmachine.CMResult;
import com.radixdlt.constraintmachine.ConstraintMachine;
import com.radixdlt.engine.StateCheckResult.StateCheckResultAcceptor;
import com.radixdlt.store.CMStore;
import com.radixdlt.store.SpinStateTransitionValidator;
import com.radixdlt.store.SpinStateTransitionValidator.TransitionCheckResult;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Top Level Class for the Radix Engine, a real-time, shardable, distributed state machine.
 */
public final class RadixEngine {
	private final ConstraintMachine constraintMachine;
	private final CMStore cmStore;

	public RadixEngine(ConstraintMachine constraintMachine, CMStore cmStore) {
		this.constraintMachine = constraintMachine;
		this.cmStore = constraintMachine.virtualize(cmStore);
	}

	public CMResult validate(ImmutableAtom atom) {
		return constraintMachine.validate(atom, false);
	}

	public StateCheckResult stateCheck(CMAtom cmAtom) {
		final ImmutableAtom atom = cmAtom.getAtom();
		// TODO: Optimize these collectors out
		Map<TransitionCheckResult, List<Pair<DataPointer, TransitionCheckResult>>> spinCheckResults = cmAtom.getParticles()
			.stream()
			.map(cmParticle -> {
				// First spun is the only one we need to check
				final Spin nextSpin = cmParticle.getNextSpin();
				final Particle particle = cmParticle.getParticle();
				final DataPointer dataPointer = cmParticle.getDataPointer();
				final TransitionCheckResult spinCheck = SpinStateTransitionValidator.checkParticleTransition(
					particle,
					nextSpin, cmStore
				);

				return Pair.of(dataPointer, spinCheck);
			})
			.collect(Collectors.groupingBy(Pair::getSecond));

		if (spinCheckResults.get(TransitionCheckResult.MISSING_STATE_FROM_UNSUPPORTED_SHARD) != null) {
			// Could be missing state needed from other shards. This is okay.
			// TODO: Log
		}

		if (spinCheckResults.get(TransitionCheckResult.ILLEGAL_TRANSITION_TO) != null ||
			spinCheckResults.get(TransitionCheckResult.MISSING_STATE) != null) {
			throw new IllegalStateException("Should not be here. This should be caught by Constraint Machine Stateless validation.");
		}

		if (spinCheckResults.get(TransitionCheckResult.CONFLICT) != null) {
			// TODO !!! This is a hack! What if there are multiple conflicts in an atom?
			// TODO !!! Current conflict handling only supports one conflicting particle.
			// TODO !!! This should be investigated and fixed asap. See RLAU-1076.
			// TODO !!! This also rejects multiple internal conflicts, which may not be ideal.
			final Pair<DataPointer, TransitionCheckResult> issue = spinCheckResults.get(TransitionCheckResult.CONFLICT).get(0);
			final SpunParticle issueParticle = issue.getFirst().getParticleFrom(atom);

			// TODO: Refactor so that two DB fetches aren't required to get conflicting atoms
			// TODO Because we're checking SpunParticles I understand there can only be one of
			// them in store as they are unique.
			//
			// Modified StateProviderFromStore.getAtomsContaining to be singular based on the
			// above assumption.
			final ImmutableAtom conflictAtom = cmStore.getAtomContaining(issueParticle);

			return acceptor -> acceptor.onConflict(issueParticle, conflictAtom);
		}

		// TODO: Add ALL missing dependencies for optimization
		if (spinCheckResults.get(TransitionCheckResult.MISSING_DEPENDENCY) != null)  {
			Pair<DataPointer, TransitionCheckResult> issue = spinCheckResults.get(TransitionCheckResult.MISSING_DEPENDENCY).get(0);
			SpunParticle issueParticle = issue.getFirst().getParticleFrom(atom);
			return acceptor -> acceptor.onMissingDependency(issueParticle);
		}

		return StateCheckResultAcceptor::onSuccess;
	}
}
