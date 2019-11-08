package com.radixdlt.engine;

import com.radixdlt.atomos.Result;
import com.radixdlt.constraintmachine.DataPointer;
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.constraintmachine.Spin;
import com.radixdlt.constraintmachine.CMErrorCode;
import com.radixdlt.constraintmachine.CMInstruction;
import com.radixdlt.constraintmachine.CMError;
import com.radixdlt.constraintmachine.CMMicroInstruction;
import com.radixdlt.constraintmachine.CMMicroInstruction.CMMicroOp;
import com.radixdlt.constraintmachine.ConstraintMachine;
import com.radixdlt.store.CMStore;
import com.radixdlt.store.CMStores;
import com.radixdlt.store.EngineStore;
import com.radixdlt.store.SpinStateMachine;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.UnaryOperator;

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
		private final AtomEventListener<U> listener;

		StoreAtom(U cmAtom, AtomEventListener<U> listener) {
			this.cmAtom = cmAtom;
			this.listener = listener;
		}
	}

	private final ConstraintMachine constraintMachine;
	private final CMStore virtualizedCMStore;

	private final EngineStore<T> engineStore;
	private final CopyOnWriteArrayList<AtomEventListener<T>> atomEventListeners = new CopyOnWriteArrayList<>();
	private final CopyOnWriteArrayList<CMSuccessHook<T>> cmSuccessHooks = new CopyOnWriteArrayList<>();
	private	final BlockingQueue<EngineAction<T>> commitQueue = new LinkedBlockingQueue<>();

	private volatile Thread stateUpdateEngine = null;
	private final Object stateUpdateEngineLock = new Object();

	public RadixEngine(
		ConstraintMachine constraintMachine,
		UnaryOperator<CMStore> virtualStoreLayer,
		EngineStore<T> engineStore
	) {
		this.constraintMachine = constraintMachine;
		// Remove cm virtual store
		this.virtualizedCMStore = virtualStoreLayer.apply(CMStores.empty());
		this.engineStore = engineStore;
	}

	private void run() {
		while (this.stateUpdateEngine != null) {
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

	/**
	 * Start this instance of the Radix Engine processing incoming events.
	 * Events are placed onto a queue by the {@link #delete(RadixEngineAtom)}
	 * and {@link #store(RadixEngineAtom, AtomEventListener)} methods.
	 * <p>
	 * In order to initially start the engine processing events, this method
	 * needs to be called after instance creation and required callbacks have
	 * been added.
	 * <p>
	 * Note that the engine instance can be restarted as many time as necessary
	 * after {@link #stop()} has been called.
	 *
	 * @return {@code true} if the call to this method actually started
	 * 		processing, {@code false} otherwise.
	 */
	public boolean start() {
		synchronized (stateUpdateEngineLock) {
			if (this.stateUpdateEngine == null) {
				this.stateUpdateEngine = new Thread(this::run);
				this.stateUpdateEngine.setDaemon(true);
				this.stateUpdateEngine.setName("Radix Engine");
				return true;
			}
			return false;
		}
	}

	/**
	 * Stop this instance of the Radix Engine from processing incoming events.
	 * Event processing may be restarted by calling the {@link #start()} method.
	 *
	 * @return {@code true} if the call to this method actually stopped
	 * 		processing, {@code false} otherwise.
	 */
	public boolean stop() {
		synchronized (stateUpdateEngineLock) {
			if (this.stateUpdateEngine != null) {
				// Reset thread variable here, so we can restart
				// if an exception occurs that we don't handle.
				Thread t = this.stateUpdateEngine;
				this.stateUpdateEngine = null;
				t.interrupt();
				try {
					t.join();
				} catch (InterruptedException e) {
					// Continue without waiting further
					Thread.currentThread().interrupt();
				}
				return true;
			}
			return false;
		}
	}

	public void addCMSuccessHook(CMSuccessHook<T> hook) {
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

		final Optional<CMError> error = constraintMachine.validate(cmAtom.getCMInstruction());
		if (error.isPresent()) {
			atomEventListener.onCMError(cmAtom, error.get());
			this.atomEventListeners.forEach(acceptor -> acceptor.onCMError(cmAtom, error.get()));
			return;
		}

		for (CMSuccessHook<T> hook : cmSuccessHooks) {
			Result hookResult = hook.hook(cmAtom);
			if (hookResult.isError()) {
				CMError cmError = new CMError(DataPointer.ofAtom(), CMErrorCode.HOOK_ERROR, null, hookResult.getErrorMessage());
				atomEventListener.onCMError(cmAtom, cmError);
				this.atomEventListeners.forEach(acceptor -> acceptor.onCMError(cmAtom, cmError));
				return;
			}
		}

		this.commitQueue.add(new StoreAtom<>(cmAtom, atomEventListener));

		atomEventListener.onCMSuccess(cmAtom);
		this.atomEventListeners.forEach(acceptor -> acceptor.onCMSuccess(cmAtom));
	}

	private void stateCheckAndStore(StoreAtom<T> storeAtom) {
		final T cmAtom = storeAtom.cmAtom;
		final CMInstruction cmInstruction = cmAtom.getCMInstruction();

		int particleIndex = 0;
		int particleGroupIndex = 0;
		for (CMMicroInstruction microInstruction : cmInstruction.getMicroInstructions()) {
			// Treat check spin as the first push for now
			if (!microInstruction.isCheckSpin()) {
				if (microInstruction.getMicroOp() == CMMicroOp.PARTICLE_GROUP) {
					particleGroupIndex++;
					particleIndex = 0;
				} else {
					particleIndex++;
				}
				continue;
			}

			final Particle particle = microInstruction.getParticle();
			if (!engineStore.supports(particle.getDestinations())) {
				continue;
			}

			final DataPointer dp = DataPointer.ofParticle(particleGroupIndex, particleIndex);

			// First spun is the only one we need to check
			final Spin checkSpin = microInstruction.getCheckSpin();
			final Spin virtualSpin = virtualizedCMStore.getSpin(particle);
			if (SpinStateMachine.isBefore(checkSpin, virtualSpin)) {
				storeAtom.listener.onVirtualStateConflict(cmAtom, dp);
				atomEventListeners.forEach(listener -> listener.onVirtualStateConflict(cmAtom, dp));
				return;
			}

			final Spin nextSpin = SpinStateMachine.next(checkSpin);
			final Spin physicalSpin = engineStore.getSpin(particle);
			final Spin currentSpin = SpinStateMachine.isAfter(virtualSpin, physicalSpin) ? virtualSpin : physicalSpin;
			if (!SpinStateMachine.canTransition(currentSpin, nextSpin)) {
				if (!SpinStateMachine.isBefore(currentSpin, nextSpin)) {
					// TODO: Refactor so that two DB fetches aren't required to get conflicting atoms
					// TODO Because we're checking SpunParticles I understand there can only be one of
					// them in store as they are unique.
					//
					// Modified StateProviderFromStore.getAtomsContaining to be singular based on the
					// above assumption.
					engineStore.getAtomContaining(particle, nextSpin == Spin.DOWN, conflictAtom -> {
						storeAtom.listener.onStateConflict(cmAtom, dp, conflictAtom);
						atomEventListeners.forEach(listener -> listener.onStateConflict(cmAtom, dp, conflictAtom));
					});

					return;
				} else {
					storeAtom.listener.onStateMissingDependency(cmAtom, dp);
					atomEventListeners.forEach(listener -> listener.onStateMissingDependency(cmAtom, dp));
					return;
				}
			}
		}

		engineStore.storeAtom(cmAtom);
		storeAtom.listener.onStateStore(cmAtom);
		atomEventListeners.forEach(listener -> listener.onStateStore(cmAtom));
	}
}
