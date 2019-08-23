package com.radixdlt.engine;

import com.google.common.collect.ImmutableSet;
import com.radixdlt.atoms.Particle;
import com.radixdlt.atoms.Spin;
import com.radixdlt.atoms.SpunParticle;
import com.radixdlt.common.Pair;
import com.radixdlt.compute.AtomCompute;
import com.radixdlt.constraintmachine.CMErrors;
import com.radixdlt.constraintmachine.CMInstruction;
import com.radixdlt.constraintmachine.CMError;
import com.radixdlt.constraintmachine.ConstraintMachine;
import com.radixdlt.constraintmachine.KernelProcedureError;
import com.radixdlt.store.CMStore;
import com.radixdlt.store.EngineStore;
import com.radixdlt.store.SpinStateTransitionValidator;
import com.radixdlt.store.SpinStateTransitionValidator.TransitionCheckResult;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Top Level Class for the Radix Engine, a real-time, shardable, distributed state machine.
 */
public final class RadixEngine<T extends RadixEngineAtom> {

	private interface EngineAction<U extends RadixEngineAtom> {
	}

	private final class DeleteAtom<U extends RadixEngineAtom> implements EngineAction<U> {
		private final U cmAtom;
		DeleteAtom(U cmAtom) {
			this.cmAtom = cmAtom;
		}
	}

	private final class StoreAtom<U extends RadixEngineAtom> implements EngineAction<U> {
		private final U cmAtom;
		private final Object computed;
		private final AtomEventListener<U> listener;

		StoreAtom(U cmAtom, Object computed, AtomEventListener<U> listener) {
			this.cmAtom = cmAtom;
			this.computed = computed;
			this.listener = listener;
		}
	}

	private final ConstraintMachine constraintMachine;
	private final CMStore virtualizedCMStore;

	private final Function<T, Optional<KernelProcedureError>> atomCheck;
	private final AtomCompute<T> compute;
	private final EngineStore<T> engineStore;
	private final CopyOnWriteArrayList<AtomEventListener<T>> atomEventListeners = new CopyOnWriteArrayList<>();
	private final CopyOnWriteArrayList<BiConsumer<T, Object>> cmSuccessHooks = new CopyOnWriteArrayList<>();
	private	final BlockingQueue<EngineAction<T>> commitQueue = new LinkedBlockingQueue<>();
	private final Thread stateUpdateEngine;

	public RadixEngine(
		ConstraintMachine constraintMachine,
		Function<T, Optional<KernelProcedureError>> atomCheck,
		AtomCompute<T> compute,
		EngineStore<T> engineStore
	) {
		this.constraintMachine = constraintMachine;
		this.atomCheck = atomCheck;
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
				final EngineAction<T> action = this.commitQueue.take();
				if (action instanceof StoreAtom) {
					StoreAtom<T> storeAtom = (StoreAtom<T>) action;
					stateCheckAndStore(storeAtom);
				} else if (action instanceof DeleteAtom) {
					DeleteAtom<T> deleteAtom = (DeleteAtom<T>) action;
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

	public void addCMSuccessHook(BiConsumer<T, Object> hook) {
		this.cmSuccessHooks.add(hook);
	}

	public void addAtomEventListener(AtomEventListener<T> acceptor) {
		this.atomEventListeners.add(acceptor);
	}

	public void delete(T cmAtom) {
		this.commitQueue.add(new DeleteAtom<>(cmAtom));
	}

	// TODO use reactive interface
	public void store(T cmAtom, AtomEventListener<T> atomEventListener) {
		Objects.requireNonNull(cmAtom);
		Objects.requireNonNull(atomEventListener);

		final Optional<KernelProcedureError> kernelError = atomCheck.apply(cmAtom);
		if (kernelError.isPresent()) {
			CMError cmError = CMErrors.fromKernelProcedureError(kernelError.get());
			atomEventListener.onCMError(cmAtom, ImmutableSet.of(cmError));
			this.atomEventListeners.forEach(acceptor -> acceptor.onCMError(cmAtom, ImmutableSet.of(cmError)));
			return;
		}

		final Optional<CMError> error = constraintMachine.validate(cmAtom.getCMInstruction());
		if (!error.isPresent()) {
			Object computed = compute.compute(cmAtom);
			this.cmSuccessHooks.forEach(hook -> hook.accept(cmAtom, computed));
			this.commitQueue.add(new StoreAtom<>(cmAtom, computed, atomEventListener));

			atomEventListener.onCMSuccess(cmAtom, computed);
			this.atomEventListeners.forEach(acceptor -> acceptor.onCMSuccess(cmAtom, computed));
		} else {
			atomEventListener.onCMError(cmAtom, ImmutableSet.of(error.get()));
			this.atomEventListeners.forEach(acceptor -> acceptor.onCMError(cmAtom, ImmutableSet.of(error.get())));
		}
	}

	private void stateCheckAndStore(StoreAtom<T> storeAtom) {
		final T cmAtom = storeAtom.cmAtom;
		final CMInstruction cmInstruction = cmAtom.getCMInstruction();
		final Object computed = storeAtom.computed;

		// TODO: Optimize these collectors out
		Map<TransitionCheckResult, List<Pair<SpunParticle, TransitionCheckResult>>> spinCheckResults = cmInstruction.getParticles()
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
			engineStore.getAtomContaining(issueParticle, conflictAtom -> {
				storeAtom.listener.onStateConflict(cmAtom, issueParticle, conflictAtom);
				atomEventListeners.forEach(listener -> listener.onStateConflict(cmAtom, issueParticle, conflictAtom));
			});

			return;
		}

		// TODO: Add ALL missing dependencies for optimization
		if (spinCheckResults.get(TransitionCheckResult.MISSING_DEPENDENCY) != null)  {
			Pair<SpunParticle, TransitionCheckResult> issue = spinCheckResults.get(TransitionCheckResult.MISSING_DEPENDENCY).get(0);
			SpunParticle issueParticle = issue.getFirst();

			storeAtom.listener.onStateMissingDependency(cmAtom, issueParticle);
			atomEventListeners.forEach(listener -> listener.onStateMissingDependency(cmAtom, issueParticle));
			return;
		}

		engineStore.storeAtom(cmAtom, computed);

		storeAtom.listener.onStateStore(cmAtom, computed);
		atomEventListeners.forEach(listener -> listener.onStateStore(cmAtom, computed));
	}
}
