package org.radix.api.observable;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.stream.Stream;

import org.radix.api.AtomQuery;
import org.radix.api.observable.AtomEventDto.AtomEventType;
import org.radix.atoms.AtomDiscoveryRequest;
import org.radix.atoms.AtomStore;
import org.radix.atoms.events.AtomDeletedEvent;
import org.radix.atoms.events.AtomStoredEvent;
import org.radix.atoms.events.PreparedAtomEvent;
import org.radix.discovery.DiscoveryException;
import org.radix.logging.Logger;
import org.radix.logging.Logging;
import org.radix.modules.Modules;

import com.radixdlt.atoms.Atom;

public class AtomEventObserver {
	private static final Logger log = Logging.getLogger("api");

	private final ConcurrentHashMap<Atom, String> processedAtoms = new ConcurrentHashMap<>();
	private final AtomQuery atomQuery;
	private final Consumer<ObservedAtomEvents> onNext;
	private final AtomDiscoveryRequest request;
	private final AtomicBoolean synced = new AtomicBoolean(false);
	private final AtomicBoolean cancelled = new AtomicBoolean(false);
	private CompletableFuture<?> currentRunnable;
	private CompletableFuture<?> firstRunnable;
	private final ExecutorService executorService;

	private void checkForDuplicates(Atom atom, String method) {
		if (processedAtoms.containsKey(atom)) {
			System.out.println("Duplicate in " + method + " atom: " + processedAtoms.get(atom));
		} else {
			processedAtoms.put(atom, method);
		}
	}

	public AtomEventObserver(
		AtomQuery atomQuery,
		Consumer<ObservedAtomEvents> onNext,
		ExecutorService executorService
	) {
		this.atomQuery = atomQuery;
		this.onNext = onNext;
		this.request = atomQuery.toAtomDiscovery();
		this.executorService = executorService;
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
		synchronized(this) {
			if (firstRunnable != null) {
				firstRunnable.cancel(true);
			}
		}
	}

	public void start() {
		synchronized (this) {
			this.currentRunnable = CompletableFuture.runAsync(() -> this.update(null), executorService);
			this.firstRunnable = currentRunnable;
		}
	}

	public void tryNext(PreparedAtomEvent atomEvent) {
		if (atomEvent instanceof AtomStoredEvent || atomEvent instanceof AtomDeletedEvent) {
			if (atomQuery.filter(atomEvent.getPreparedAtom())) {
				final AtomEventType atomEventType = atomEvent instanceof AtomStoredEvent ? AtomEventType.STORE : AtomEventType.DELETE;
				final AtomEventDto atomEventDto = new AtomEventDto(atomEventType, atomEvent.getAtom());
				synchronized (this) {
					this.currentRunnable = currentRunnable.thenRunAsync(() -> update(atomEventDto), executorService);
				}
			}
		}
	}

	private void update(AtomEventDto atomEventDto) {
		if (cancelled.get()) {
			return;
		}


		if (atomEventDto != null && synced.get()) {
			onNext.accept(new ObservedAtomEvents(true, Stream.of(atomEventDto)));
		} else {

			// FIXME: There are clearly race conditions and ordeing issues now that Atom DELETE's have been introduced.
			// FIXME: But too hard to fix at the moment without deep refactors of the database and event handling.
			if (atomEventDto != null && atomEventDto.getType().equals(AtomEventType.DELETE)) {
				onNext.accept(new ObservedAtomEvents(false, Stream.of(atomEventDto)));
			}

			try {
				long count = 0;
				do {
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
								this.update(null);
							}, executorService);
						}
						return;
					}

					Modules.get(AtomStore.class).discovery(request);

					if (!request.getDelivered().isEmpty()) {
						request.getDelivered().forEach(raw -> checkForDuplicates(raw, atomEventDto == null ? "discovery" : "event"));

						if (atomEventDto != null
							&& atomEventDto.getType().equals(AtomEventType.STORE)
							&& request.getDelivered().size() == 1) {
							if (request.getDelivered().get(0).equals(atomEventDto.getAtom())) {
								synced.set(true);
							}
						}

						final Stream<AtomEventDto> atomEvents = request.getDelivered().stream()
							.map(atom -> new AtomEventDto(AtomEventType.STORE, atom));
						onNext.accept(new ObservedAtomEvents(false, atomEvents));
					}

					count += request.getDelivered().size();

				} while (request.next());

				// Send HEAD flag once we've read through all atoms
				onNext.accept(new ObservedAtomEvents(true, Stream.empty()));
			} catch (DiscoveryException e) {
				log.error("While handling atom event update", e);
			}
		}
	}
}
