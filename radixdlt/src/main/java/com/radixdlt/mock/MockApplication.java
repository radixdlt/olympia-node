package com.radixdlt.mock;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.radixdlt.Atom;
import com.radixdlt.common.AID;
import com.radixdlt.ledger.Ledger;
import com.radixdlt.ledger.LedgerIndex;
import com.radixdlt.ledger.exceptions.LedgerIndexConflictException;
import org.radix.logging.Logger;
import org.radix.logging.Logging;

import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A simple mock application for testing ledgers.
 */
public final class MockApplication {
	private static final Logger logger = Logging.getLogger("mock.app");

	private static AtomicInteger applicationId = new AtomicInteger(0);

	private final Ledger ledger;
	private final BlockingQueue<Atom> atomQueue;
	private final ConcurrentMap<AID, ImmutableSet<AID>> conflictRemnants;
	private final int id;
	private final MockAccessor mockAccessor;

	public MockApplication(Ledger ledger) {
		this.ledger = Objects.requireNonNull(ledger);

		this.atomQueue = new LinkedBlockingQueue<>();
		this.conflictRemnants = new ConcurrentHashMap<>();
		this.id = applicationId.getAndIncrement();
		this.mockAccessor = new MockAccessor(this);
	}

	/**
	 * Starts an instance of this application.
	 * Creates a new thread and application every time it is called.
	 */
	public void startInstance() {
		Thread mainThread = new Thread(this::run, "Mock Application " + this.id);
		mainThread.start();
		Thread producerThread = new Thread(this::consume, "Mock Consumer " + this.id);
		producerThread.start();
		logger.info("Started '" + mainThread.getName() + "'");
	}

	// consume ledger into queue
	private void consume() {
		while (true) {
			try {
				Atom atom = ledger.receive();
				atomQueue.put(atom);
			} catch (InterruptedException e) {
				// exit if interrupted
				Thread.currentThread().interrupt();
				break;
			} catch (Exception e) {
				logger.error("Error in consumer loop: '" + e.toString() + "'", e);
			}
		}
	}

	// process queue
	private void run() {
		while (true) {
			try {
				Atom atom = atomQueue.take();
				Object content = atom.getContent();

				// figure out indices
				ImmutableSet<LedgerIndex> uniqueIndices = ImmutableSet.of();
				ImmutableSet<LedgerIndex> duplicateIndices = ImmutableSet.of();
				if (content instanceof MockAtomContent) {
					uniqueIndices = MockAtomIndexer.getUniqueIndices((MockAtomContent) content);
					duplicateIndices = MockAtomIndexer.getDuplicateIndices((MockAtomContent) content);
				} else {
					logger.debug(String.format("Received foreign atom content %s, cannot infer indices", content.getClass().getSimpleName()));
				}

				ledger.store(atom, uniqueIndices, duplicateIndices);
				logger.info(String.format("Stored atom '%s'", atom.getAID()));
			} catch (InterruptedException e) {
				// exit if interrupted
				Thread.currentThread().interrupt();
				break;
			} catch (Exception e) {
				logger.error("Error in core application loop: '" + e.toString() + "'", e);
			}
		}
	}

	boolean queue(Atom winner) {
		return atomQueue.add(winner);
	}

	public MockAccessor getAccessor() {
		return this.mockAccessor;
	}
}
