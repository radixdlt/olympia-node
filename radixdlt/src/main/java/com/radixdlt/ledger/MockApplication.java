package com.radixdlt.ledger;

import com.google.common.collect.ImmutableSet;
import com.radixdlt.Atom;
import org.radix.logging.Logger;
import org.radix.logging.Logging;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A simple mock application for testing ledgers.
 */
public final class MockApplication {
	private static final Logger logger = Logging.getLogger("App");
	private static AtomicInteger applicationId = new AtomicInteger(0);
	private final Ledger ledger;

	public MockApplication(Ledger ledger) {
		this.ledger = Objects.requireNonNull(ledger);
	}

	/**
	 * Starts an instance of this application.
	 * Creates a new thread and application every time it is called.
	 */
	public void startInstance() {
		Thread thread = new Thread(this::run, "Mock Application " + applicationId.getAndIncrement());
		thread.start();
		logger.info("Started '" + thread.getName() + "'");
	}

	private void run() {
		while (true) {
			try {
				Atom atom = ledger.receive();
				ImmutableSet<LedgerIndex> uniqueIndices = ImmutableSet.of();
				ImmutableSet<LedgerIndex> duplicateIndices = ImmutableSet.of();

				if (ledger.store(atom, uniqueIndices, duplicateIndices)) {
					logger.info("Stored atom " + atom.getAID());
				}
			} catch (InterruptedException e) {
				// exit if interrupted
				Thread.currentThread().interrupt();
				break;
			} catch (Exception e) {
				logger.error("Error in core application loop", e);
			}
		}
	}
}
