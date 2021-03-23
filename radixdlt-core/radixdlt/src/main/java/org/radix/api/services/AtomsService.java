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

package org.radix.api.services;

import com.google.common.collect.ImmutableSet;
import com.radixdlt.DefaultSerialization;
import org.json.JSONException;
import org.json.JSONObject;
import org.radix.api.AtomQuery;
import org.radix.api.observable.AtomEventObserver;
import org.radix.api.observable.Disposable;
import org.radix.api.observable.ObservedAtomEvents;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.Inject;
import com.radixdlt.consensus.Command;
import com.radixdlt.crypto.Hasher;
import com.radixdlt.environment.EventDispatcher;
import com.radixdlt.identifiers.AID;
import com.radixdlt.mempool.MempoolAdd;
import com.radixdlt.mempool.MempoolAddFailure;
import com.radixdlt.atom.Atom;
import com.radixdlt.serialization.DeserializeException;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.Serialization;
import com.radixdlt.statecomputer.AtomsCommittedToLedger;
import com.radixdlt.statecomputer.AtomsRemovedFromMempool;
import com.radixdlt.store.AtomIndex;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

import static java.util.Optional.empty;
import static java.util.Optional.of;
import static java.util.Optional.ofNullable;

public class AtomsService {
	private static final int NUMBER_OF_THREADS = 8;
	/**
	 * Some of these may block for a short while so keep a few.
	 * TODO: remove the blocking
	 */
	private static final ExecutorService executorService = Executors.newFixedThreadPool(
		NUMBER_OF_THREADS,
		new ThreadFactoryBuilder().setNameFormat("AtomsService-%d").build()
	);

	private final Set<AtomEventObserver> atomEventObservers = Sets.newConcurrentHashSet();
	private final Object singleAtomObserversLock = new Object();
	private final Map<AID, List<AtomStatusListener>> singleAtomObservers = Maps.newHashMap();

	private final AtomIndex store;
	private final CompositeDisposable disposable;

	private final EventDispatcher<MempoolAdd> mempoolAddEventDispatcher;
	private final Observable<AtomsRemovedFromMempool> mempoolAtomsRemoved;
	private final Observable<MempoolAddFailure> mempoolAddFailures;
	private final Observable<AtomsCommittedToLedger> ledgerCommitted;

	private final Hasher hasher;
	private final Serialization serialization;

	@Inject
	public AtomsService(
		Observable<AtomsRemovedFromMempool> mempoolAtomsRemoved,
		Observable<MempoolAddFailure> mempoolAddFailures,
		Observable<AtomsCommittedToLedger> ledgerCommitted,
		AtomIndex store,
		EventDispatcher<MempoolAdd> mempoolAddEventDispatcher,
		Hasher hasher,
		Serialization serialization
	) {
		this.mempoolAtomsRemoved = Objects.requireNonNull(mempoolAtomsRemoved);
		this.mempoolAddFailures = Objects.requireNonNull(mempoolAddFailures);
		this.mempoolAddEventDispatcher = mempoolAddEventDispatcher;
		this.store = Objects.requireNonNull(store);
		this.disposable = new CompositeDisposable();
		this.ledgerCommitted = ledgerCommitted;
		this.hasher = hasher;
		this.serialization = serialization;
	}

	public void start() {
		var lastStoredAtomDisposable = ledgerCommitted
			.observeOn(Schedulers.io())
			.subscribe(this::processExecutedCommand);
		this.disposable.add(lastStoredAtomDisposable);

		var submissionFailuresDisposable = mempoolAddFailures
			.observeOn(Schedulers.io())
			.subscribe(this::processSubmissionFailure);
		this.disposable.add(submissionFailuresDisposable);

		var mempoolAtomsRemovedDisposable = mempoolAtomsRemoved
			.observeOn(Schedulers.io())
			.subscribe(this::processSubmissionFailure);
		this.disposable.add(mempoolAtomsRemovedDisposable);

	}

	public void stop() {
		this.disposable.dispose();
	}

	public AID submitAtom(JSONObject jsonAtom) {
		// TODO: remove all of the conversion mess here
		final var atom = this.serialization.fromJsonObject(jsonAtom, Atom.class);
		var command = new Command(serialization.toDson(atom, Output.ALL));
		this.mempoolAddEventDispatcher.dispatch(MempoolAdd.create(command));
		return command.getId();
	}

	public Disposable subscribeAtomStatusNotifications(AID aid, AtomStatusListener subscriber) {
		addAtomStatusListener(aid, subscriber);
		return () -> removeAtomStatusListener(aid, subscriber);
	}

	public org.radix.api.observable.Observable<ObservedAtomEvents> getAtomEvents(AtomQuery atomQuery) {
		return observer -> {
			final var atomEventObserver = createAtomObserver(atomQuery, observer);
			atomEventObserver.start();
			this.atomEventObservers.add(atomEventObserver);

			return () -> {
				this.atomEventObservers.remove(atomEventObserver);
				atomEventObserver.cancel();
			};
		};
	}

	public long getWaitingCount() {
		return this.atomEventObservers.stream().map(AtomEventObserver::isDone).filter(done -> !done).count();
	}

	public Optional<JSONObject> getAtomByAtomId(AID atomId) throws JSONException {
		return store.get(atomId)
			.map(atom -> serialization.toJsonObject(atom, DsonOutput.Output.API));
	}

	private AtomEventObserver createAtomObserver(AtomQuery atomQuery, Consumer<ObservedAtomEvents> observer) {
		return new AtomEventObserver(
			atomQuery, observer, executorService, store, serialization, hasher
		);
	}

	private void processExecutedCommand(AtomsCommittedToLedger atomsCommittedToLedger) {
		atomsCommittedToLedger.getAtoms().forEach(cmd -> {
			// TODO: Temporary, remove at some point
			final Atom atom;
			try {
				atom = DefaultSerialization.getInstance().fromDson(cmd.getPayload(), Atom.class);
			} catch (DeserializeException e) {
				throw new IllegalStateException();
			}
			var indicies = atom.upParticles()
				.flatMap(p -> p.getDestinations().stream())
				.collect(ImmutableSet.toImmutableSet());

			this.atomEventObservers.forEach(observer -> observer.tryNext(atom, cmd.getId(), indicies));
			getAtomStatusListeners(cmd.getId()).forEach(listener -> listener.onStored(cmd.getId()));
		});
	}

	private void processSubmissionFailure(MempoolAddFailure failure) {
		var atomId = failure.getCommand().getId();
		var listeners = getAtomStatusListeners(atomId);
		listeners.forEach(listener -> listener.onError(failure.getException()));
	}

	private void processSubmissionFailure(AtomsRemovedFromMempool atomsRemovedFromMempool) {
		atomsRemovedFromMempool.forEach((cmd, e) -> {
			final AID aid = cmd.getId();
			getAtomStatusListeners(aid).forEach(listener -> listener.onError(e));
		});
	}

	private Optional<Atom> toClientAtom(final byte[] payload) {
		try {
			return of(serialization.fromDson(payload, Atom.class));
		} catch (DeserializeException e) {
			return empty();
		}
	}

	private ImmutableList<AtomStatusListener> getAtomStatusListeners(AID aid) {
		synchronized (this.singleAtomObserversLock) {
			return getListeners(this.singleAtomObservers, aid);
		}
	}

	private void addAtomStatusListener(AID aid, AtomStatusListener listener) {
		synchronized (this.singleAtomObserversLock) {
			addListener(this.singleAtomObservers, aid, listener);
		}
	}

	private void removeAtomStatusListener(AID aid, AtomStatusListener listener) {
		synchronized (this.singleAtomObserversLock) {
			removeListener(this.singleAtomObservers, aid, listener);
		}
	}

	private static <T> ImmutableList<T> getListeners(Map<AID, List<T>> listenersMap, AID aid) {
		List<T> listeners = listenersMap.get(aid);
		return (listeners == null) ? ImmutableList.of() : ImmutableList.copyOf(listeners);
	}

	private static <T> void addListener(Map<AID, List<T>> listenersMap, AID aid, T listener) {
		List<T> listeners = listenersMap.computeIfAbsent(aid, id -> Lists.newArrayList());
		listeners.add(listener);
	}

	private static <T> void removeListener(Map<AID, List<T>> listenersMap, AID aid, T listener) {
		ofNullable(listenersMap.get(aid)).stream()
			.peek(listeners -> listeners.remove(listener))
			.filter(List::isEmpty)
			.findFirst().ifPresent(__ -> listenersMap.remove(aid));
	}
}
