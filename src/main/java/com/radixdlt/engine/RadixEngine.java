package com.radixdlt.engine;

import com.google.common.collect.ImmutableSet;
import com.radixdlt.atoms.Particle;
import com.radixdlt.atoms.Spin;
import com.radixdlt.atoms.SpunParticle;
import com.radixdlt.common.Pair;
import com.radixdlt.compute.AtomCompute;
import com.radixdlt.constraintmachine.CMAtom;
import com.radixdlt.constraintmachine.CMError;
import com.radixdlt.constraintmachine.ConstraintMachine;
import com.radixdlt.store.CMStore;
import com.radixdlt.store.EngineStore;
import com.radixdlt.store.SpinStateTransitionValidator;
import com.radixdlt.store.SpinStateTransitionValidator.TransitionCheckResult;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

/**
 * Top Level Class for the Radix Engine, a real-time, shardable, distributed state machine.
 */
public final class RadixEngine {

	private interface EngineAction {
	}

	private final class DeleteAtom implements EngineAction {
		private final CMAtom cmAtom;
		DeleteAtom(CMAtom cmAtom) {
			this.cmAtom = cmAtom;
		}
	}

	private final class StoreAtom implements EngineAction {
		private final CMAtom cmAtom;
		private final Object computed;
		StoreAtom(CMAtom cmAtom, Object computed) {
			this.cmAtom = cmAtom;
			this.computed = computed;
		}
	}

	private final ConstraintMachine constraintMachine;
	private final AtomCompute compute;
	private final EngineStore engineStore;
	private final CMStore virtualizedCMStore;
	private final CopyOnWriteArrayList<AtomEventListener> atomEventListeners = new CopyOnWriteArrayList<>();
	private final CopyOnWriteArrayList<BiConsumer<CMAtom, Object>> cmSuccessHooks = new CopyOnWriteArrayList<>();
	private	final BlockingQueue<EngineAction> commitQueue = new LinkedBlockingQueue<>();
	private final Thread stateUpdateEngine;

	public RadixEngine(
		ConstraintMachine constraintMachine,
		AtomCompute compute,
		EngineStore engineStore
	) {
		this.constraintMachine = constraintMachine;
		this.compute = compute;
		this.virtualizedCMStore = constraintMachine.getVirtualStore().apply(engineStore);
		this.engineStore = engineStore;
		this.stateUpdateEngine = new Thread(this::run);
		this.stateUpdateEngine.setDaemon(true);
		this.stateUpdateEngine.setName("Radix Engine");
	}

	private void run() {
		while (true) {
			try {
				final EngineAction action = this.commitQueue.take();
				if (action instanceof StoreAtom) {
					StoreAtom storeAtom = (StoreAtom) action;
					stateCheckAndStore(storeAtom.cmAtom, storeAtom.computed);
				} else if (action instanceof DeleteAtom) {
					DeleteAtom deleteAtom = (DeleteAtom) action;
					engineStore.deleteAtom(deleteAtom.cmAtom);
				}
			} catch (InterruptedException e) {
				// Just exit if we are interrupted
				Thread.currentThread().interrupt();
				break;
			}
		}
	}

	// TODO: temporary interface, remove in favor of reactive-streams
	public int getCommitQueueSize() {
		return commitQueue.size();
	}

	public void start() {
		stateUpdateEngine.start();
	}

	public void addCMSuccessHook(BiConsumer<CMAtom, Object> hook) {
		this.cmSuccessHooks.add(hook);
	}

	public void addAtomEventListener(AtomEventListener acceptor) {
		this.atomEventListeners.add(acceptor);
	}

	public void delete(CMAtom cmAtom) {
		this.commitQueue.add(new DeleteAtom(cmAtom));
	}

	public void store(CMAtom cmAtom) {
		final Optional<CMError> error = constraintMachine.validate(cmAtom);
		if (!error.isPresent()) {
			Object computed = compute.compute(cmAtom);
			this.cmSuccessHooks.forEach(hook -> hook.accept(cmAtom, computed));
			this.commitQueue.add(new StoreAtom(cmAtom, computed));
			this.atomEventListeners.forEach(acceptor -> acceptor.onCMSuccess(cmAtom, computed));
		} else {
			this.atomEventListeners.forEach(acceptor -> acceptor.onCMError(cmAtom, ImmutableSet.of(error.get())));
		}
	}

	private void stateCheckAndStore(CMAtom cmAtom, Object computed) {
		// TODO: Optimize these collectors out
		Map<TransitionCheckResult, List<Pair<SpunParticle, TransitionCheckResult>>> spinCheckResults = cmAtom.getParticles()
			.stream()
			.map(cmParticle -> {
				// First spun is the only one we need to check
				final Spin nextSpin = cmParticle.getNextSpin();
				final Particle particle = cmParticle.getParticle();
				final TransitionCheckResult spinCheck = SpinStateTransitionValidator.checkParticleTransition(
					particle,
					nextSpin,
					virtualizedCMStore
				);

				return Pair.of(SpunParticle.of(particle, nextSpin), spinCheck);
			})
			.collect(Collectors.groupingBy(Pair::getSecond));

		//if (spinCheckResults.get(TransitionCheckResult.MISSING_STATE_FROM_UNSUPPORTED_SHARD) != null) {
			// Could be missing state needed from other shards. This is okay.
			// TODO: Log
		//}

		if (spinCheckResults.get(TransitionCheckResult.ILLEGAL_TRANSITION_TO) != null
			|| spinCheckResults.get(TransitionCheckResult.MISSING_STATE) != null) {
			throw new IllegalStateException("Should not be here. This should be caught by Constraint Machine Stateless validation.");
		}

		if (spinCheckResults.get(TransitionCheckResult.CONFLICT) != null) {
			final Pair<SpunParticle, TransitionCheckResult> issue = spinCheckResults.get(TransitionCheckResult.CONFLICT).get(0);
			final SpunParticle issueParticle = issue.getFirst();

			// TODO: Refactor so that two DB fetches aren't required to get conflicting atoms
			// TODO Because we're checking SpunParticles I understand there can only be one of
			// them in store as they are unique.
			//
			// Modified StateProviderFromStore.getAtomsContaining to be singular based on the
			// above assumption.
			engineStore.getAtomContaining(issueParticle, conflictAtom ->
				atomEventListeners.forEach(listener -> listener.onStateConflict(cmAtom, issueParticle, conflictAtom))
			);

			return;
		}

		// TODO: Add ALL missing dependencies for optimization
		if (spinCheckResults.get(TransitionCheckResult.MISSING_DEPENDENCY) != null)  {
			Pair<SpunParticle, TransitionCheckResult> issue = spinCheckResults.get(TransitionCheckResult.MISSING_DEPENDENCY).get(0);
			SpunParticle issueParticle = issue.getFirst();
			atomEventListeners.forEach(listener -> listener.onStateMissingDependency(cmAtom, issueParticle));
			return;
		}

		engineStore.storeAtom(cmAtom, computed);

		atomEventListeners.forEach(listener -> listener.onStateStore(cmAtom, computed));
	}
}
