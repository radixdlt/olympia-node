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

import com.radixdlt.DefaultSerialization;
import com.radixdlt.api.DeserializationFailure;
import com.radixdlt.api.CommittedAtomsRx;
import com.radixdlt.api.SubmissionErrorsRx;
import com.radixdlt.api.SubmissionFailure;
import com.radixdlt.atommodel.Atom;
import com.radixdlt.consensus.bft.BFTCommittedUpdate;
import com.radixdlt.consensus.bft.PreparedVertex;
import com.radixdlt.engine.RadixEngineException;
import com.radixdlt.crypto.Hasher;
import com.radixdlt.mempool.MempoolRejectedException;
import com.radixdlt.mempool.SubmissionControl;

import com.radixdlt.middleware2.store.StoredCommittedCommand;
import com.radixdlt.statecomputer.ClientAtomToBinaryConverter;
import com.radixdlt.statecomputer.CommittedAtom;
import com.radixdlt.statecomputer.RadixEngineStateComputer.CommittedAtomWithResult;
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

import com.radixdlt.middleware2.store.CommandToBinaryConverter;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.Serialization;
import com.radixdlt.store.LedgerEntry;
import com.radixdlt.store.LedgerEntryStore;

import java.util.concurrent.atomic.AtomicReference;
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
	private final Map<AID, List<AtomStatusListener>> singleAtomObserversx = Maps.newHashMap();
	private final Object deleteOnEventSingleAtomObserversLock = new Object();
	private final Map<AID, List<SingleAtomListener>> deleteOnEventSingleAtomObserversx = Maps.newHashMap();

	private final Serialization serialization = DefaultSerialization.getInstance();

	private final SubmissionControl submissionControl;
	private final CommandToBinaryConverter commandToBinaryConverter;
	private final ClientAtomToBinaryConverter clientAtomToBinaryConverter;
	private final LedgerEntryStore store;
	private final CompositeDisposable disposable;

	private final SubmissionErrorsRx submissionErrorsRx;
	private final CommittedAtomsRx committedAtomsRx;
	private final Observable<BFTCommittedUpdate> committedUpdates;

	private final Hasher hasher;

	public AtomsService(
		SubmissionErrorsRx submissionErrorsRx,
		CommittedAtomsRx committedAtomsRx,
		Observable<BFTCommittedUpdate> committedUpdates,
		LedgerEntryStore store,
		SubmissionControl submissionControl,
		CommandToBinaryConverter commandToBinaryConverter,
		ClientAtomToBinaryConverter clientAtomToBinaryConverter,
		Hasher hasher
	) {
		this.submissionErrorsRx = Objects.requireNonNull(submissionErrorsRx);
		this.submissionControl = Objects.requireNonNull(submissionControl);
		this.store = Objects.requireNonNull(store);
		this.commandToBinaryConverter = Objects.requireNonNull(commandToBinaryConverter);
		this.clientAtomToBinaryConverter = Objects.requireNonNull(clientAtomToBinaryConverter);
		this.disposable = new CompositeDisposable();
		this.committedAtomsRx = committedAtomsRx;
		this.committedUpdates = Objects.requireNonNull(committedUpdates);
		this.hasher = hasher;
	}

	private void processExecutedCommand(CommittedAtomWithResult committedAtomWithResult) {
		committedAtomWithResult.ifSuccess(indicies -> {
			final CommittedAtom committedAtom = committedAtomWithResult.getCommittedAtom();
			final AID aid = committedAtom.getAID();
			this.atomEventObservers.forEach(observer -> observer.tryNext(committedAtom, indicies));
			getSingleAtomListeners(aid).forEach(SingleAtomListener::onStored);
			getAtomStatusListeners(aid).forEach(listener -> listener.onStored(committedAtom));
		});
	}

	private void processExecutionFailure(ClientAtom atom, RadixEngineException e) {
		getSingleAtomListeners(atom.getAID()).forEach(listener -> listener.onStoredFailure(e));
		getAtomStatusListeners(atom.getAID()).forEach(listener -> listener.onStoredFailure(e));
	}


	private void processSubmissionFailure(SubmissionFailure e) {
		final AID aid = e.getClientAtom().getAID();
		removeSingleAtomListeners(aid).forEach(listener -> listener.onError(aid, e.getException()));
		getAtomStatusListeners(aid).forEach(listener -> listener.onError(e.getException()));
	}

	private void processDeserializationFailure(DeserializationFailure e) {
		final AID aid = Atom.aidOf(e.getAtom(), this.hasher);
		removeSingleAtomListeners(aid).forEach(listener -> listener.onError(aid, e.getException()));
		getAtomStatusListeners(aid).forEach(listener -> listener.onError(e.getException()));
	}

	public void start() {
		var lastStoredAtomDisposable = committedAtomsRx.committedAtoms()
			.observeOn(Schedulers.io())
			.subscribe(this::processExecutedCommand);
		this.disposable.add(lastStoredAtomDisposable);

		var committedUpdatesDisposable = committedUpdates
			.observeOn(Schedulers.io())
			.subscribe(update ->
				update.getCommitted().stream()
					.flatMap(PreparedVertex::errorCommands)
					.forEach(cmdErr -> {
						ClientAtom clientAtom = cmdErr.getFirst().map(clientAtomToBinaryConverter::toAtom);
						Exception e = cmdErr.getSecond();
						if (e instanceof RadixEngineException) {
							this.processExecutionFailure(clientAtom, (RadixEngineException) e);
						}
					})
				);
		this.disposable.add(committedUpdatesDisposable);

		var submissionFailuresDisposable = submissionErrorsRx.submissionFailures()
			.observeOn(Schedulers.io())
			.subscribe(this::processSubmissionFailure);
		this.disposable.add(submissionFailuresDisposable);

		var deserializationFailures = submissionErrorsRx.deserializationFailures()
			.observeOn(Schedulers.io())
			.subscribe(this::processDeserializationFailure);
		this.disposable.add(deserializationFailures);
	}

	public void stop() {
		this.disposable.dispose();
	}

	public AID submitAtom(JSONObject jsonAtom, SingleAtomListener subscriber) {
		AtomicReference<AID> aid = new AtomicReference<>();
		try {
			this.submissionControl.submitAtom(jsonAtom, atom -> {
				aid.set(atom.getAID());
				subscribeToSubmission(subscriber, atom);
			});
			return aid.get();
		} catch (MempoolRejectedException e) {
			if (subscriber != null) {
				AID atomId = aid.get();
				removeSingleAtomListener(atomId, subscriber);
				subscriber.onError(atomId, e);
			}
			throw new IllegalStateException(e);
		}
	}

	private void subscribeToSubmission(SingleAtomListener subscriber, ClientAtom atom) {
		if (subscriber != null) {
			addSingleAtomListener(atom.getAID(), subscriber);
		}
	}

	public Disposable subscribeAtomStatusNotifications(AID aid, AtomStatusListener subscriber) {
		addAtomStatusListener(aid, subscriber);
		return () -> removeAtomStatusListener(aid, subscriber);
	}

	public org.radix.api.observable.Observable<ObservedAtomEvents> getAtomEvents(AtomQuery atomQuery) {
		return observer -> {
			final AtomEventObserver atomEventObserver = new AtomEventObserver(atomQuery, observer, executorService, store, commandToBinaryConverter, clientAtomToBinaryConverter, hasher);
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

	public JSONObject getAtomsByAtomId(AID atomId) throws JSONException {
		Optional<LedgerEntry> ledgerEntryOptional = store.get(atomId);
		if (ledgerEntryOptional.isPresent()) {
			LedgerEntry ledgerEntry = ledgerEntryOptional.get();
			StoredCommittedCommand committedCommand = commandToBinaryConverter.toCommand(ledgerEntry.getContent());
			ClientAtom clientAtom = committedCommand.getCommand().map(clientAtomToBinaryConverter::toAtom);
			Atom apiAtom = ClientAtom.convertToApiAtom(clientAtom);
			return serialization.toJsonObject(apiAtom, DsonOutput.Output.API);
		}
		throw new RuntimeException("Atom not found");
	}

	private ImmutableList<AtomStatusListener> getAtomStatusListeners(AID aid) {
		synchronized (this.singleAtomObserversLock) {
			return getListeners(this.singleAtomObserversx, aid);
		}
	}

	private void addAtomStatusListener(AID aid, AtomStatusListener listener) {
		synchronized (this.singleAtomObserversLock) {
			addListener(this.singleAtomObserversx, aid, listener);
		}
	}
	private void removeAtomStatusListener(AID aid, AtomStatusListener listener) {
		synchronized (this.singleAtomObserversLock) {
			removeListener(this.singleAtomObserversx, aid, listener);
		}
	}

	private ImmutableList<SingleAtomListener> getSingleAtomListeners(AID aid) {
		synchronized (this.deleteOnEventSingleAtomObserversLock) {
			return getListeners(this.deleteOnEventSingleAtomObserversx, aid);
		}
	}

	private void addSingleAtomListener(AID aid, SingleAtomListener listener) {
		synchronized (this.deleteOnEventSingleAtomObserversLock) {
			addListener(this.deleteOnEventSingleAtomObserversx, aid, listener);
		}
	}

	private void removeSingleAtomListener(AID aid, SingleAtomListener listener) {
		synchronized (this.deleteOnEventSingleAtomObserversLock) {
			removeListener(this.deleteOnEventSingleAtomObserversx, aid, listener);
		}
	}

	private List<SingleAtomListener> removeSingleAtomListeners(AID aid) {
		synchronized (this.deleteOnEventSingleAtomObserversLock) {
			List<SingleAtomListener> listeners = this.deleteOnEventSingleAtomObserversx.remove(aid);
			return (listeners == null) ? List.of() : listeners; // No need to make copy - removed
		}
	}

	private <T> ImmutableList<T> getListeners(Map<AID, List<T>> listenersMap, AID aid) {
		List<T> listeners = listenersMap.get(aid);
		return (listeners == null) ? ImmutableList.of() : ImmutableList.copyOf(listeners);
	}

	private <T> void addListener(Map<AID, List<T>> listenersMap, AID aid, T listener) {
		List<T> listeners = listenersMap.computeIfAbsent(aid, id -> Lists.newArrayList());
		listeners.add(listener);
	}

	private <T> void removeListener(Map<AID, List<T>> listenersMap, AID aid, T listener) {
		List<T> listeners = listenersMap.get(aid);
		if (listeners != null) {
			listeners.remove(listener);
			if (listeners.isEmpty()) {
				listenersMap.remove(aid);
			}
		}
	}
}
