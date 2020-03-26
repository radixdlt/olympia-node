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

import com.google.common.collect.EvictingQueue;
import com.radixdlt.common.Atom;
import com.radixdlt.mempool.MempoolRejectedException;
import com.radixdlt.mempool.SubmissionControl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.radixdlt.middleware2.converters.AtomToBinaryConverter;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.Serialization;
import com.radixdlt.store.LedgerEntry;
import com.radixdlt.store.LedgerEntryStore;

import org.json.JSONException;
import org.json.JSONObject;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import org.radix.api.observable.AtomEventDto;
import org.radix.api.observable.AtomEventDto.AtomEventType;
import org.radix.api.AtomQuery;
import org.radix.api.observable.AtomEventObserver;
import org.radix.api.observable.Disposable;
import org.radix.api.observable.ObservedAtomEvents;
import org.radix.api.observable.Observable;
import org.radix.atoms.events.AtomEvent;
import org.radix.atoms.events.AtomExceptionEvent;
import org.radix.atoms.events.AtomStoredEvent;
import com.radixdlt.common.AID;
import org.radix.events.Events;

public class AtomsService {
	private static int  NUMBER_OF_THREADS = 8;
	/**
	 * Some of these may block for a short while so keep a few.
	 * TODO: remove the blocking
	 */
	private final static ExecutorService executorService = Executors.newFixedThreadPool(
		NUMBER_OF_THREADS,
		new ThreadFactoryBuilder().setNameFormat("AtomsService-%d").build()
	);

	private final Set<AtomEventObserver> atomEventObservers = Collections.newSetFromMap(new ConcurrentHashMap<>());
	private final ConcurrentHashMap<AID, List<AtomStatusListener>> singleAtomObservers = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<AID, List<SingleAtomListener>> deleteOnEventSingleAtomObservers = new ConcurrentHashMap<>();

	private final ConcurrentHashMap<AtomEventDto.AtomEventType, Long> atomEventCount = new ConcurrentHashMap<>();
	private final Serialization serialization = Serialization.getDefault();

	private final Object lock = new Object();
	private final EvictingQueue<String> eventRingBuffer = EvictingQueue.create(64);
	private final SubmissionControl submissionControl;
	private final AtomToBinaryConverter atomToBinaryConverter;
	private final LedgerEntryStore store;

	public AtomsService(LedgerEntryStore store, SubmissionControl submissionControl, AtomToBinaryConverter atomToBinaryConverter) {
		this.submissionControl = Objects.requireNonNull(submissionControl);
		this.store = Objects.requireNonNull(store);
		this.atomToBinaryConverter = Objects.requireNonNull(atomToBinaryConverter);

		Events.getInstance().register(AtomEvent.class, (event) -> {
			executorService.submit(() -> {
				if (!(event instanceof AtomEvent)) {
					return;
				}

				final AtomEvent atomEvent = (AtomEvent) event;
				final Atom atom = atomEvent.getAtom();

				// TODO: Clean this up
				final String eventName;

				if (event instanceof AtomStoredEvent) {
					final AtomStoredEvent storedEvent = (AtomStoredEvent) atomEvent;
					eventName = "STORED";

					this.atomEventObservers.forEach(observer -> observer.tryNext(storedEvent));

					List<SingleAtomListener> subscribers = this.deleteOnEventSingleAtomObservers.remove(atom.getAID());
					if (subscribers != null) {
						Iterator<SingleAtomListener> i = subscribers.iterator();
						if (i.hasNext()) {
							i.next().onStored(true);
							while (i.hasNext()) {
								i.next().onStored(false);
							}
						}
					}

					for (AtomStatusListener atomStatusListener : this.singleAtomObservers.getOrDefault(atom.getAID(), Collections.emptyList())) {
						atomStatusListener.onStored();
					}

					this.atomEventCount.merge(AtomEventType.STORE, 1L, Long::sum);
				} else {
					eventName = "UNKNOWN";
				}

				synchronized (lock) {
					eventRingBuffer.add(System.currentTimeMillis() + " " + eventName + " " + atomEvent.getAtom().getAID());
				}
			});
		});

		Events.getInstance().register(AtomExceptionEvent.class, event -> {
			executorService.submit(() -> {
				final AtomExceptionEvent exceptionEvent = (AtomExceptionEvent) event;
				synchronized (lock) {
					eventRingBuffer.add(
						System.currentTimeMillis() + " EXCEPTION " + exceptionEvent.getAtomId()
							+ " " + exceptionEvent.getException().getClass().getName());
				}

				List<SingleAtomListener> subscribers = this.deleteOnEventSingleAtomObservers.remove(exceptionEvent.getAtomId());
				if (subscribers != null) {
					Throwable exception = exceptionEvent.getException();
					subscribers.forEach(subscriber -> subscriber.onError(exceptionEvent.getAtomId(), exception));
				}

				for (AtomStatusListener singleAtomListener : this.singleAtomObservers.getOrDefault(exceptionEvent.getAtomId(), Collections.emptyList())) {
					Throwable exception = exceptionEvent.getException();
					singleAtomListener.onError(exception);
				}
			});
		});
	}

	/**
	 * Get a list of the most recent events
	 * @return list of event strings
	 */
	public List<String> getEvents() {
		synchronized (lock) {
			return new ArrayList<>(eventRingBuffer);
		}
	}

	public Map<AtomEventType, Long> getAtomEventCount() {
		return Collections.unmodifiableMap(atomEventCount);
	}

	public AID submitAtom(JSONObject jsonAtom, SingleAtomListener subscriber) {
		try {
			return this.submissionControl.submitAtom(jsonAtom, atom -> subscribeToSubmission(subscriber, atom));
		} catch (MempoolRejectedException e) {
			if (subscriber != null) {
				AID atomId = e.atom().getAID();
				this.deleteOnEventSingleAtomObservers.computeIfPresent(atomId, (aid, subscribers) -> {
					subscribers.remove(subscriber);
					return subscribers;
				});
				subscriber.onError(atomId, e);
			}
			throw new IllegalStateException(e);
		}
	}

	private void subscribeToSubmission(SingleAtomListener subscriber, Atom atom) {
		if (subscriber != null) {
			this.deleteOnEventSingleAtomObservers.compute(atom.getAID(), (aid, oldSubscribers) -> {
				List<SingleAtomListener> subscribers = oldSubscribers == null ? new ArrayList<>() : oldSubscribers;
				subscribers.add(subscriber);
				return subscribers;
			});
		}
	}

	public Disposable subscribeAtomStatusNotifications(AID aid, AtomStatusListener subscriber) {
		this.singleAtomObservers.compute(aid, (hid, oldSubscribers) -> {
			List<AtomStatusListener> subscribers = oldSubscribers == null ? new ArrayList<>() : oldSubscribers;
			subscribers.add(subscriber);
			return subscribers;
		});

		return () -> this.singleAtomObservers.get(aid).remove(subscriber);
	}

	public Observable<ObservedAtomEvents> getAtomEvents(AtomQuery atomQuery) {
		return observer -> {
			final AtomEventObserver atomEventObserver = new AtomEventObserver(atomQuery, observer, executorService, store, atomToBinaryConverter);
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
			Atom atom = atomToBinaryConverter.toAtom(ledgerEntry.getContent());
			return serialization.toJsonObject(atom, DsonOutput.Output.API);
		}
		throw new RuntimeException("Atom not found");
	}
}
