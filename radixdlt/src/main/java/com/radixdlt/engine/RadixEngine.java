package com.radixdlt.engine;

import com.google.common.collect.ImmutableSet;
import com.radixdlt.atoms.DataPointer;
import com.radixdlt.atoms.ImmutableAtom;
import com.radixdlt.atoms.Particle;
import com.radixdlt.atoms.Spin;
import com.radixdlt.atoms.SpunParticle;
import com.radixdlt.common.Pair;
import com.radixdlt.compute.AtomCompute;
import com.radixdlt.constraintmachine.CMAtom;
import com.radixdlt.constraintmachine.CMError;
import com.radixdlt.constraintmachine.ConstraintMachine;
import com.radixdlt.store.CMStore;
import com.radixdlt.store.SpinStateTransitionValidator;
import com.radixdlt.store.SpinStateTransitionValidator.TransitionCheckResult;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * Top Level Class for the Radix Engine, a real-time, shardable, distributed state machine.
 */
public final class RadixEngine {
	private final ConstraintMachine constraintMachine;
	private final AtomCompute compute;
	private final CMStore cmStore;
	private final CopyOnWriteArrayList<AtomEventListener> atomEventListeners = new CopyOnWriteArrayList<>();

	public RadixEngine(ConstraintMachine constraintMachine, AtomCompute compute, CMStore cmStore) {
		this.constraintMachine = constraintMachine;
		this.compute = compute;
		this.cmStore = constraintMachine.getVirtualStore().apply(cmStore);
	}

	public void addAtomEventListener(AtomEventListener acceptor) {
		this.atomEventListeners.add(acceptor);
	}

	public void removeAtomEventListener(AtomEventListener acceptor) {
		this.atomEventListeners.remove(acceptor);
	}

	public void validate(CMAtom cmAtom) {
		final ImmutableSet<CMError> errors = constraintMachine.validate(cmAtom, false);
		if (errors.isEmpty()) {
			atomEventListeners.forEach(acceptor -> acceptor.onCMSuccess(cmAtom, compute.compute(cmAtom)));
		} else {
			atomEventListeners.forEach(acceptor -> acceptor.onCMError(cmAtom, errors));
		}
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

			return acceptor -> acceptor.onConflict(cmAtom, issueParticle, conflictAtom);
		}

		// TODO: Add ALL missing dependencies for optimization
		if (spinCheckResults.get(TransitionCheckResult.MISSING_DEPENDENCY) != null)  {
			Pair<DataPointer, TransitionCheckResult> issue = spinCheckResults.get(TransitionCheckResult.MISSING_DEPENDENCY).get(0);
			SpunParticle issueParticle = issue.getFirst().getParticleFrom(atom);
			return acceptor -> acceptor.onMissingDependency(cmAtom, issueParticle);
		}

		return acceptor -> acceptor.onSuccess(cmAtom);
	}
}
