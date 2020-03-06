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

package com.radixdlt.engine;

import com.radixdlt.atomos.Result;
import com.radixdlt.atommodel.Atom;
import com.radixdlt.constraintmachine.DataPointer;
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.constraintmachine.Spin;
import com.radixdlt.constraintmachine.CMErrorCode;
import com.radixdlt.constraintmachine.CMInstruction;
import com.radixdlt.constraintmachine.CMError;
import com.radixdlt.constraintmachine.CMMicroInstruction;
import com.radixdlt.constraintmachine.CMMicroInstruction.CMMicroOp;
import com.radixdlt.constraintmachine.ConstraintMachine;
import com.radixdlt.middleware.RadixEngineUtils;
import com.radixdlt.middleware.SimpleRadixEngineAtom;
import com.radixdlt.store.CMStore;
import com.radixdlt.store.CMStores;
import com.radixdlt.store.EngineStore;
import com.radixdlt.store.SpinStateMachine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.UnaryOperator;

/**
 * Top Level Class for the Radix Engine, a real-time, shardable, distributed state machine.
 */
public final class RadixEngine {

	private static final Logger log = LoggerFactory.getLogger(RadixEngine.class);

	private interface EngineAction {
	}

	private final class DeleteAtom implements EngineAction {
		private final Atom atom;
		DeleteAtom(Atom atom) {
			this.atom = atom;
		}
	}

	private final class StoreAtom implements EngineAction {
		private final Atom atom;
		private final AtomEventListener listener;

		StoreAtom(Atom atom, AtomEventListener listener) {
			this.atom = atom;
			this.listener = listener;
		}
	}

	private final ConstraintMachine constraintMachine;
	private final CMStore virtualizedCMStore;

	private final EngineStore engineStore;
	private final CopyOnWriteArrayList<AtomEventListener> atomEventListeners = new CopyOnWriteArrayList<>();
	private final CopyOnWriteArrayList<CMSuccessHook> cmSuccessHooks = new CopyOnWriteArrayList<>();
	private	final BlockingQueue<EngineAction> commitQueue = new LinkedBlockingQueue<>();

	private volatile boolean running = false;
	private Thread stateUpdateThread = null;
	private final Object stateUpdateEngineLock = new Object();

	public RadixEngine(
		ConstraintMachine constraintMachine,
		UnaryOperator<CMStore> virtualStoreLayer,
		EngineStore engineStore
	) {
		this.constraintMachine = constraintMachine;
		// Remove cm virtual store
		this.virtualizedCMStore = virtualStoreLayer.apply(CMStores.empty());
		this.engineStore = engineStore;
	}

	private void run() {
		while (this.running) {
			try {
				final EngineAction action = this.commitQueue.take();
				if (action instanceof StoreAtom) {
					StoreAtom storeAtom = (StoreAtom) action;
					stateCheckAndStore(storeAtom);
				} else if (action instanceof DeleteAtom) {
					DeleteAtom deleteAtom = (DeleteAtom) action;
					engineStore.deleteAtom(deleteAtom.atom.getAID());
				} else {
					// We don't want to stop processing future EngineActions,
					// but we do want to flag this logic error.
					log.error("Unknown EngineAction: {}", action.getClass().getName());
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
	 * Events are placed onto a queue by the {@link #delete(Atom)}
	 * and {@link #store(Atom, AtomEventListener)} methods.
	 * <p>
	 * In order to initially start the engine processing events, this method
	 * needs to be called after instance creation.
	 * <p>
	 * Note that the engine instance can be restarted as many time as necessary
	 * after {@link #stop()} has been called.
	 *
	 * @return {@code true} if the call to this method actually started
	 * 		processing, {@code false} otherwise.
	 */
	public boolean start() {
		synchronized (stateUpdateEngineLock) {
			if (!this.running) {
				this.stateUpdateThread = new Thread(this::run);
				this.stateUpdateThread.setDaemon(true);
				this.stateUpdateThread.setName("Radix Engine");
				this.running = true;
				this.stateUpdateThread.start();
				return true;
			}
			return false;
		}
	}

	/**
	 * Stop this instance of the Radix Engine from processing incoming events.
	 * Event processing may be restarted by calling the {@link #start()} method.
	 * <p>
	 * It is not necessary to call {@code stop()} before exiting the main
	 * thread in order to exit the JVM when using instances of this class.
	 *
	 * @return {@code true} if the call to this method actually stopped
	 * 		processing, {@code false} otherwise.
	 */
	public boolean stop() {
		synchronized (stateUpdateEngineLock) {
			if (this.running) {
				try {
					this.stateUpdateThread.interrupt();
					this.stateUpdateThread.join();
				} catch (InterruptedException e) {
					// Continue without waiting further
					Thread.currentThread().interrupt();
				} finally {
					// Reset thread variable here, so we can restart
					// if an exception occurs that we don't handle.
					this.stateUpdateThread = null;
					this.running = false;
				}
				return true;
			}
			return false;
		}
	}

	public void addCMSuccessHook(CMSuccessHook hook) {
		this.cmSuccessHooks.add(hook);
	}

	public void addAtomEventListener(AtomEventListener acceptor) {
		this.atomEventListeners.add(acceptor);
	}

	public void delete(Atom atom) {
		this.commitQueue.add(new DeleteAtom(atom));
	}

	public Optional<CMError> staticCheck(Atom atom) {
		RadixEngineAtom cmAtom;
		try {
			cmAtom = RadixEngineUtils.toCMAtom(atom);
		} catch (RadixEngineUtils.CMAtomConversionException e) {
			return Optional.of(new CMError(e.getDataPointer(), CMErrorCode.INVALID_PARTICLE, null));
		}
		return constraintMachine.validate(cmAtom.getCMInstruction());
	}

	// TODO use reactive interface
	public void store(Atom atom, AtomEventListener atomEventListener) {
		Objects.requireNonNull(atom);
		Objects.requireNonNull(atomEventListener);

		RadixEngineAtom cmAtom;
		try {
			cmAtom = RadixEngineUtils.toCMAtom(atom);
		} catch (RadixEngineUtils.CMAtomConversionException e) {
			log.error("Atom creation failed", e);
			CMError cmError = new CMError(e.getDataPointer(), CMErrorCode.INVALID_PARTICLE, null);
			atomEventListener.onCMError(atom, cmError);
			this.atomEventListeners.forEach(acceptor -> acceptor.onCMError(atom, cmError));
			return;
		}
		final Optional<CMError> error = constraintMachine.validate(cmAtom.getCMInstruction());
		if (error.isPresent()) {
			log.error("Atom is not valid: {}", error.get());
			atomEventListener.onCMError(atom, error.get());
			this.atomEventListeners.forEach(acceptor -> acceptor.onCMError(atom, error.get()));
			return;
		}

		for (CMSuccessHook hook : cmSuccessHooks) {
			Result hookResult = hook.hook(atom);
			if (hookResult.isError()) {
				CMError cmError = new CMError(DataPointer.ofAtom(), CMErrorCode.HOOK_ERROR, null, hookResult.getErrorMessage());
				atomEventListener.onCMError(atom, cmError);
				this.atomEventListeners.forEach(acceptor -> acceptor.onCMError(atom, cmError));
				return;
			}
		}

		this.commitQueue.add(new StoreAtom(atom, atomEventListener));

		atomEventListener.onCMSuccess(atom);
		this.atomEventListeners.forEach(acceptor -> acceptor.onCMSuccess(atom));
	}

	private void stateCheckAndStore(StoreAtom storeAtom) {
		final Atom atom = storeAtom.atom;
		try {
			SimpleRadixEngineAtom cmAtom = RadixEngineUtils.toCMAtom(atom);
			final CMInstruction cmInstruction = cmAtom.getCMInstruction();

			long particleIndex = 0;
			long particleGroupIndex = 0;
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
					storeAtom.listener.onVirtualStateConflict(atom, dp);
					atomEventListeners.forEach(listener -> listener.onVirtualStateConflict(atom, dp));
					return;
				}

				final Spin nextSpin = SpinStateMachine.next(checkSpin);
				final Spin physicalSpin = engineStore.getSpin(particle);
				final Spin currentSpin = SpinStateMachine.isAfter(virtualSpin, physicalSpin) ? virtualSpin : physicalSpin;
				if (!SpinStateMachine.canTransition(currentSpin, nextSpin)) {
					if (!SpinStateMachine.isBefore(currentSpin, nextSpin)) {
						engineStore.getAtomContaining(particle, nextSpin == Spin.DOWN, conflictAtom -> {
							storeAtom.listener.onStateConflict(atom, dp, conflictAtom);
							atomEventListeners.forEach(listener -> listener.onStateConflict(atom, dp, conflictAtom));
						});

						return;
					} else {
						storeAtom.listener.onStateMissingDependency(atom.getAID(), particle);
						atomEventListeners.forEach(listener -> listener.onStateMissingDependency(atom.getAID(), particle));
						return;
					}
				}
			}

			engineStore.storeAtom(atom);
			storeAtom.listener.onStateStore(atom);
			atomEventListeners.forEach(listener -> listener.onStateStore(atom));
		} catch (RadixEngineUtils.CMAtomConversionException e) {
			log.error("Atom creation failed", e);
		}
	}
}
