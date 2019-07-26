package com.radixdlt.engine;

import com.google.common.collect.ImmutableMap;
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
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

/**
 * Top Level Class for the Radix Engine, a real-time, shardable, distributed state machine.
 */
public final class RadixEngine {
	private final ConstraintMachine constraintMachine;
	private final AtomCompute compute;
	private final CMStore cmStore;
	private final CopyOnWriteArrayList<AtomEventListener> atomEventListeners = new CopyOnWriteArrayList<>();
	private final CopyOnWriteArrayList<BiConsumer<CMAtom, ImmutableMap<String, Object>>> cmSuccessHooks = new CopyOnWriteArrayList<>();
	private	final BlockingQueue<Pair<CMAtom, ImmutableMap<String, Object>>> commitQueue = new LinkedBlockingQueue<>();
	private final Thread stateUpdateEngine;

	public RadixEngine(
		ConstraintMachine constraintMachine,
		AtomCompute compute,
		CMStore cmStore
	) {
		this.constraintMachine = constraintMachine;
		this.compute = compute;
		this.cmStore = constraintMachine.getVirtualStore().apply(cmStore);
		this.stateUpdateEngine = new Thread(() -> {
			while (true) {
				final Pair<CMAtom, ImmutableMap<String, Object>> massedAtom;
				try {
					massedAtom = this.commitQueue.poll(1, TimeUnit.SECONDS);
				} catch (InterruptedException e) {
					// Just exit if we are interrupted
					Thread.currentThread().interrupt();
					break;
				}

				if (massedAtom == null)
					continue;

				stateCheck(massedAtom.getFirst(), massedAtom.getSecond());
			}
		});
		this.stateUpdateEngine.setName("Radix Engine");
	}

	public void start() {
		stateUpdateEngine.start();
	}

	public void addCMSuccessHook(BiConsumer<CMAtom, ImmutableMap<String, Object>> hook) {
		this.cmSuccessHooks.add(hook);
	}

	public void removeCMSuccessHook(BiConsumer<CMAtom, ImmutableMap<String, Object>> hook) {
		this.cmSuccessHooks.remove(hook);
	}

	public void addAtomEventListener(AtomEventListener acceptor) {
		this.atomEventListeners.add(acceptor);
	}

	public void removeAtomEventListener(AtomEventListener acceptor) {
		this.atomEventListeners.remove(acceptor);
	}

	// TODO: replace with reactive-streams interface
	public void submit(CMAtom cmAtom) {
		final ImmutableSet<CMError> errors = constraintMachine.validate(cmAtom, false);
		if (errors.isEmpty()) {
			ImmutableMap<String, Object> computed = compute.compute(cmAtom);
			this.cmSuccessHooks.forEach(hook -> hook.accept(cmAtom, computed));
			this.commitQueue.add(Pair.of(cmAtom, computed));
			this.atomEventListeners.forEach(acceptor -> acceptor.onCMSuccess(cmAtom, computed));
		} else {
			this.atomEventListeners.forEach(acceptor -> acceptor.onCMError(cmAtom, errors));
		}
	}

	private void stateCheck(CMAtom cmAtom, ImmutableMap<String, Object> computed) {
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
			final Pair<DataPointer, TransitionCheckResult> issue = spinCheckResults.get(TransitionCheckResult.CONFLICT).get(0);
			final SpunParticle issueParticle = issue.getFirst().getParticleFrom(atom);

			// TODO: Refactor so that two DB fetches aren't required to get conflicting atoms
			// TODO Because we're checking SpunParticles I understand there can only be one of
			// them in store as they are unique.
			//
			// Modified StateProviderFromStore.getAtomsContaining to be singular based on the
			// above assumption.
			final ImmutableAtom conflictAtom = cmStore.getAtomContaining(issueParticle);

			atomEventListeners.forEach(listener -> listener.onStateConflict(cmAtom, issueParticle, conflictAtom));
			return;
		}

		// TODO: Add ALL missing dependencies for optimization
		if (spinCheckResults.get(TransitionCheckResult.MISSING_DEPENDENCY) != null)  {
			Pair<DataPointer, TransitionCheckResult> issue = spinCheckResults.get(TransitionCheckResult.MISSING_DEPENDENCY).get(0);
			SpunParticle issueParticle = issue.getFirst().getParticleFrom(atom);
			atomEventListeners.forEach(listener -> listener.onStateMissingDependency(cmAtom, issueParticle));
			return;
		}

		cmStore.storeAtom(cmAtom, computed);

		atomEventListeners.forEach(listener -> listener.onStateStore(cmAtom, computed));
	}
}
