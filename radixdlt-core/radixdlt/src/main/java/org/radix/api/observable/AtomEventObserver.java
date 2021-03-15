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

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.radixdlt.atom.Atom;
import com.radixdlt.constraintmachine.CMMicroInstruction;
import com.radixdlt.crypto.Hasher;
import com.radixdlt.identifiers.AID;
import com.radixdlt.identifiers.EUID;
import com.radixdlt.atom.ClientAtom;
import com.radixdlt.serialization.Serialization;
import com.radixdlt.statecomputer.CommittedAtom;
import com.radixdlt.store.LedgerEntryStore;
import com.radixdlt.store.SearchCursor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.radix.api.AtomQuery;
import org.radix.api.observable.AtomEventDto.AtomEventType;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class AtomEventObserver {
	private static final Logger log = LogManager.getLogger();

	private static final int BATCH_SIZE = 50;

	private final AtomQuery atomQuery;
	private final Consumer<ObservedAtomEvents> onNext;
	private final AtomicBoolean cancelled = new AtomicBoolean(false);
	private CompletableFuture<?> currentRunnable;
	private CompletableFuture<?> firstRunnable;
	private final ExecutorService executorService;
	private final LedgerEntryStore store;
	private final Serialization serialization;
	private final Hasher hasher;

	private final Object syncLock = new Object();
	private boolean synced = false;
	private final List<AtomEventDto> waitingQueue = Lists.newArrayList();

	public AtomEventObserver(
		AtomQuery atomQuery,
		Consumer<ObservedAtomEvents> onNext,
		ExecutorService executorService,
		LedgerEntryStore store,
		Serialization serialization,
		Hasher hasher
	) {
		this.atomQuery = atomQuery;
		this.onNext = onNext;
		this.executorService = executorService;
		this.store = store;
		this.serialization = serialization;
		this.hasher = hasher;
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

	public void tryNext(CommittedAtom committedAtom, ImmutableSet<EUID> indicies) {
		if (committedAtom.getClientAtom() == null) {
			return;
		}

		if (!atomQuery.filter(indicies)) {
			return;
		}

		final AtomEventDto atomEventDto = new AtomEventDto(AtomEventType.STORE, committedAtom.getClientAtom());
		synchronized (this) {
			this.currentRunnable = currentRunnable.thenRunAsync(() -> update(atomEventDto), executorService);
		}
	}

	private void sync() {
		SearchCursor cursor = store.search();
		Set<AID> processedAtomIds = Sets.newHashSet();
		partialSync(atomQuery.getDestination(), cursor, processedAtomIds);
	}

	private void partialSync(EUID destination, SearchCursor cursor, final Set<AID> processedAtomIds) {
		long count = 0;
		try {
			while (cursor != null) {
				if (cancelled.get()) {
					return;
				}

				if (count >= 200) {
					delaySync(destination, cursor, processedAtomIds);
					return;
				}

				List<ClientAtom> atoms = new ArrayList<>();
				while (cursor != null && atoms.size() < BATCH_SIZE) {
					AID aid = cursor.get();
					processedAtomIds.add(aid);
					ClientAtom ledgerEntry = store.get(aid).orElseThrow();
					if (ledgerEntry
							.uniqueInstructions()
							.map(CMMicroInstruction::getParticle)
							.flatMap(p -> p.getDestinations().stream())
							.anyMatch(destination::equals)
					) {
						atoms.add(ledgerEntry);
					}
					cursor = cursor.next();
				}
				if (!atoms.isEmpty()) {
					final Stream<AtomEventDto> atomEvents = atoms.stream()
						.map(a -> new AtomEventDto(AtomEventType.STORE, a));
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
					.filter(aed -> {
							var aid = aed.getAtom().getAID();
							return !processedAtomIds.contains(aid)
								|| aed.getType() == AtomEventType.DELETE;
						}
					)
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

	private void delaySync(EUID destination, SearchCursor cursor, Set<AID> processedAtomIds) {
		synchronized (this) {
			this.currentRunnable = currentRunnable.thenRunAsync(() -> {
				// Hack to throttle back high amounts of atom reads
				// Will fix this once an async library is used
				try {
					TimeUnit.SECONDS.sleep(1);
				} catch (InterruptedException e) {
					// Re-interrupt and continue
					Thread.currentThread().interrupt();
				}
				this.partialSync(destination, cursor, processedAtomIds);
			}, executorService);
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
