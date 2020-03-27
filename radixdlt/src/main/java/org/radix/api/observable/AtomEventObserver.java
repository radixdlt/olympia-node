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

package org.radix.api.observable;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.radixdlt.identifiers.AID;
import com.radixdlt.atommodel.Atom;
import com.radixdlt.store.SearchCursor;
import com.radixdlt.store.StoreIndex;
import com.radixdlt.store.LedgerSearchMode;
import com.radixdlt.middleware2.converters.AtomToBinaryConverter;
import com.radixdlt.middleware2.store.EngineAtomIndices;

import com.radixdlt.store.LedgerEntry;
import com.radixdlt.store.LedgerEntryStore;
import org.radix.api.AtomQuery;
import org.radix.api.observable.AtomEventDto.AtomEventType;
import org.radix.atoms.events.AtomEventWithDestinations;
import org.radix.atoms.events.AtomStoredEvent;
import org.radix.logging.Logger;
import org.radix.logging.Logging;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class AtomEventObserver {
	private static final Logger log = Logging.getLogger("api");

	private static final int BATCH_SIZE = 50;

	private final AtomQuery atomQuery;
	private final Consumer<ObservedAtomEvents> onNext;
	private final AtomicBoolean cancelled = new AtomicBoolean(false);
	private CompletableFuture<?> currentRunnable;
	private CompletableFuture<?> firstRunnable;
	private final ExecutorService executorService;
	private final LedgerEntryStore store;
	private final AtomToBinaryConverter atomToBinaryConverter;

	private final Object syncLock = new Object();
	private boolean synced = false;
	private final List<AtomEventDto> waitingQueue = Lists.newArrayList();

	public AtomEventObserver(
		AtomQuery atomQuery,
		Consumer<ObservedAtomEvents> onNext,
		ExecutorService executorService,
		LedgerEntryStore store,
		AtomToBinaryConverter atomToBinaryConverter
	) {
		this.atomQuery = atomQuery;
		this.onNext = onNext;
		this.executorService = executorService;
		this.store = store;
		this.atomToBinaryConverter = atomToBinaryConverter;
	}

	public boolean isDone() {
		synchronized (this) {
			if (currentRunnable != null) {
				return currentRunnable.isDone();
			} else {
				return true;
			}
		}
	}

	public void cancel() {
		cancelled.set(true);
		synchronized (this) {
			if (firstRunnable != null) {
				firstRunnable.cancel(true);
			}
		}
	}

	public void start() {
		synchronized (this) {
			this.currentRunnable = CompletableFuture.runAsync(this::sync, executorService);
			this.firstRunnable = currentRunnable;
		}
	}

	public void tryNext(AtomEventWithDestinations atomEvent) {
		if (atomEvent instanceof AtomStoredEvent) {
			if (atomQuery.filter(atomEvent.getDestinations())) {
				final AtomEventType atomEventType = atomEvent instanceof AtomStoredEvent ? AtomEventType.STORE : AtomEventType.DELETE;
				final AtomEventDto atomEventDto = new AtomEventDto(atomEventType, atomEvent.getAtom());
				synchronized (this) {
					this.currentRunnable = currentRunnable.thenRunAsync(() -> update(atomEventDto), executorService);
				}
			}
		}
	}

	private void sync() {
		if (cancelled.get()) {
			return;
		}

		try {
			long count = 0;
			StoreIndex destinationIndex = new StoreIndex(EngineAtomIndices.IndexType.DESTINATION.getValue(), atomQuery.getDestination().toByteArray());
			SearchCursor cursor = store.search(StoreIndex.LedgerIndexType.DUPLICATE, destinationIndex, LedgerSearchMode.EXACT);
			Set<AID> processedAids = Sets.newHashSet();
			while (cursor != null) {
				if (count >= 200) {
					synchronized(this) {
						this.currentRunnable = currentRunnable.thenRunAsync(() -> {
							// Hack to throttle back high amounts of atom reads
							// Will fix this once an async library is used
							try {
								TimeUnit.SECONDS.sleep(1);
							} catch (InterruptedException e) {
								// Re-interrupt and continue
								Thread.currentThread().interrupt();
							}
							this.sync();
						}, executorService);
					}
					return;
				}

				List<Atom> atoms = new ArrayList<>();
				while (cursor != null && atoms.size() < BATCH_SIZE) {
					AID aid = cursor.get();
					processedAids.add(aid);
					Optional<LedgerEntry> ledgerEntry = store.get(aid);
					ledgerEntry.ifPresent(
						entry -> {
							Atom atom = atomToBinaryConverter.toAtom(entry.getContent());
							atoms.add(atom);
						}
					);
					cursor = cursor.next();
				}
				if (!atoms.isEmpty()) {
					final Stream<AtomEventDto> atomEvents = atoms.stream()
						.map(atom -> new AtomEventDto(AtomEventType.STORE, atom));
					onNext.accept(new ObservedAtomEvents(false, atomEvents));
					count += atoms.size();
				}
			}

			// Send received and queued events
			final List<AtomEventDto> atomEvents;
			synchronized (syncLock) {
				this.synced = true;
				// Note that we filter here so that the filter executes with lock held
				atomEvents = this.waitingQueue.stream()
					.filter(aed -> !processedAids.contains(aed.getAtom().getAID()) || aed.getType() == AtomEventType.DELETE)
					.collect(Collectors.toList());
				this.waitingQueue.clear();
			}
			this.onNext.accept(new ObservedAtomEvents(false, atomEvents.stream()));

			// Send HEAD flag once we've read through all atoms
			onNext.accept(new ObservedAtomEvents(true, Stream.empty()));
		} catch (Exception e) {
			log.error("While handling atom event update", e);
		}
	}

	private void update(AtomEventDto atomEventDto) {
		if (this.cancelled.get()) {
			return;
		}

		synchronized (syncLock) {
			if (!this.synced) {
				this.waitingQueue.add(atomEventDto);
				return;
			}
		}
		this.onNext.accept(new ObservedAtomEvents(true, Stream.of(atomEventDto)));
	}
}
