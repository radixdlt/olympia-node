package org.radix.api.observable;

import com.radixdlt.common.AID;
import com.radixdlt.ledger.Ledger;
import com.radixdlt.ledger.LedgerCursor;
import com.radixdlt.ledger.LedgerIndex;
import com.radixdlt.ledger.LedgerSearchMode;
import com.radixdlt.middleware.SimpleRadixEngineAtom;
import com.radixdlt.middleware2.converters.SimpleRadixEngineAtomToEngineAtom;
import com.radixdlt.middleware2.store.EngineAtomIndices;
import com.radixdlt.tempo.LegacyUtils;
import org.radix.api.AtomQuery;
import org.radix.api.observable.AtomEventDto.AtomEventType;
import org.radix.atoms.Atom;
import org.radix.atoms.AtomDiscoveryRequest;
import org.radix.atoms.events.AtomDeletedEvent;
import org.radix.atoms.events.AtomStoredEvent;
import org.radix.atoms.events.PreparedAtomEvent;
import org.radix.logging.Logger;
import org.radix.logging.Logging;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.stream.Stream;

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
	private final Ledger ledger;
	private SimpleRadixEngineAtomToEngineAtom simpleRadixEngineAtomToEngineAtom = new SimpleRadixEngineAtomToEngineAtom();

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
		ExecutorService executorService,
		Ledger ledger
	) {
		this.atomQuery = atomQuery;
		this.onNext = onNext;
		this.request = atomQuery.toAtomDiscovery();
		this.executorService = executorService;
		this.ledger = ledger;
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
		log.debug("Atom discovery request: " + request);
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
				List<com.radixdlt.Atom> atoms = new ArrayList<>();

				if (request.getDestination() != null) {
					LedgerIndex destinationIndex = new LedgerIndex(EngineAtomIndices.IndexType.DESTINATION.getValue(), request.getDestination().toByteArray());
					LedgerCursor ledgerCursor = ledger.search(LedgerIndex.LedgerIndexType.DUPLICATE, destinationIndex, LedgerSearchMode.EXACT);
					if (ledgerCursor == null) {
						log.debug("ledgerCursor is null");
					}
					while (ledgerCursor != null) {
						log.debug("ledgerCursor is not null");
						AID destinationAID = ledgerCursor.get();
						com.radixdlt.Atom destinationAtom = ledger.get(destinationAID).get();
						atoms.add(destinationAtom);
						ledgerCursor = ledgerCursor.next();
					}
				}

				log.debug("atoms size = " + atoms.size());
				Stream<AtomEventDto> atomStream = atoms.stream().map(atom -> {
					//potentially we could have performance issue here
					SimpleRadixEngineAtom simpleRadixEngineAtom = simpleRadixEngineAtomToEngineAtom.convert(atom);
					Atom legacyAtom = LegacyUtils.toLegacyAtom(simpleRadixEngineAtom);
					return new AtomEventDto(AtomEventType.STORE, legacyAtom);
				});
				onNext.accept(new ObservedAtomEvents(true, atomStream));
				synced.set(true);

				// Send HEAD flag once we've read through all atoms
				onNext.accept(new ObservedAtomEvents(true, Stream.empty()));
			} catch (Exception e) {
				log.error("While handling atom event update", e);
			}
		}
	}
}
