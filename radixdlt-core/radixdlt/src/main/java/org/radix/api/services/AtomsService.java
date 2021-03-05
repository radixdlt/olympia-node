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

import com.google.inject.Inject;
import com.radixdlt.DefaultSerialization;
import com.radixdlt.atommodel.Atom;
import com.radixdlt.consensus.Command;
import com.radixdlt.crypto.Hasher;
import com.radixdlt.environment.EventDispatcher;
import com.radixdlt.mempool.MempoolAdd;
import com.radixdlt.mempool.MempoolAddFailure;

import com.radixdlt.serialization.DeserializeException;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.statecomputer.CommittedAtom;
import com.radixdlt.statecomputer.AtomCommittedToLedger;
import com.radixdlt.statecomputer.AtomsRemovedFromMempool;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import com.radixdlt.middleware2.ClientAtom;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.Serialization;
import com.radixdlt.store.LedgerEntryStore;

import java.util.function.Consumer;

import org.json.JSONException;
import org.json.JSONObject;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import org.radix.api.AtomQuery;
import org.radix.api.observable.AtomEventObserver;
import org.radix.api.observable.Disposable;
import org.radix.api.observable.ObservedAtomEvents;
import com.radixdlt.identifiers.AID;

import static java.util.Optional.*;

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

	private final LedgerEntryStore store;
	private final CompositeDisposable disposable;

	private final EventDispatcher<MempoolAdd> mempoolAddEventDispatcher;
	private final Observable<AtomsRemovedFromMempool> mempoolAtomsRemoved;
	private final Observable<MempoolAddFailure> mempoolAddFailures;
	private final Observable<AtomCommittedToLedger> ledgerCommitted;

	private final Hasher hasher;
	private final Serialization serialization;

	@Inject
	public AtomsService(
		Observable<AtomsRemovedFromMempool> mempoolAtomsRemoved,
		Observable<MempoolAddFailure> mempoolAddFailures,
		Observable<AtomCommittedToLedger> ledgerCommitted,
		LedgerEntryStore store,
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

	public Serialization serialization() {
		return serialization;
	}

	private void processExecutedCommand(AtomCommittedToLedger atomCommittedToLedger) {
		var committedAtom = atomCommittedToLedger.getAtom();

		this.atomEventObservers.forEach(observer -> observer.tryNext(committedAtom, atomCommittedToLedger.getIndices()));
		getAtomStatusListeners(committedAtom.getAID()).forEach(listener -> listener.onStored(committedAtom));
	}

	private void processSubmissionFailure(MempoolAddFailure failure) {
		failure.getCommand()
			.map(this::toClientAtom)
			.map(ClientAtom::getAID)
			.map(this::getAtomStatusListeners)
			.ifPresent(list -> list.forEach(listener -> listener.onError(failure.getException())));
	}

	private Optional<ClientAtom> toClientAtom(final byte[] payload) {
		try {
			return of(serialization.fromDson(payload, ClientAtom.class));
		} catch (DeserializeException e) {
			return empty();
		}
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
		final var rawAtom = this.serialization.fromJsonObject(jsonAtom, Atom.class);
		final var atom = ClientAtom.convertFromApiAtom(rawAtom, hasher);

		var command = new Command(serialization.toDson(atom, Output.ALL));
		this.mempoolAddEventDispatcher.dispatch(MempoolAdd.create(command));

		return atom.getAID();
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

	private AtomEventObserver createAtomObserver(AtomQuery atomQuery, Consumer<ObservedAtomEvents> observer) {
		return new AtomEventObserver(
			atomQuery, observer, executorService, store, serialization, hasher
		);
	}

	public long getWaitingCount() {
		return this.atomEventObservers.stream().map(AtomEventObserver::isDone).filter(done -> !done).count();
	}

	public Optional<JSONObject> getAtomsByAtomId(AID atomId) throws JSONException {
		return store.get(atomId)
			.map(ClientAtom::convertToApiAtom)
			.map(apiAtom -> serialization.toJsonObject(apiAtom, DsonOutput.Output.API));
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
