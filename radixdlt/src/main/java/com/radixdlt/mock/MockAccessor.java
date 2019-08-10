package com.radixdlt.mock;

import com.radixdlt.ledger.LedgerIndex;
import com.radixdlt.utils.Ints;
import com.radixdlt.utils.Longs;
import org.radix.logging.Logger;
import org.radix.logging.Logging;

import java.util.Random;

/**
 * Public facing accessor for mocking operations in a {@link MockApplication}
 */
public final class MockAccessor {
	private static final Logger logger = Logging.getLogger("App");

	private final Random rng = new Random();
	private final MockApplication mockApplication;

	MockAccessor(MockApplication mockApplication) {
		this.mockApplication = mockApplication;
	}

	public void spawn(int atomCount) {
		logger.info("Spamming " + atomCount + " random mock atoms");
		for (int i = 0; i < atomCount; i++) {
			// generate random key / value
			byte[] keyBytes = new byte[9];
			byte[] valueBytes = Ints.toByteArray(i);
			rng.nextBytes(keyBytes);
			rng.nextBytes(valueBytes);

			LedgerIndex key = new LedgerIndex(keyBytes);
			MockAtom atom = new MockAtom(new MockAtomContent(key, valueBytes));
			mockApplication.queue(atom);
		}
	}

	public void spawnWithKey(long key, int atomCount) {
		logger.info("Spamming " + atomCount + " mock atoms with key " + key);
		for (int i = 0; i < atomCount; i++) {
			// generate random key / value
			byte[] valueBytes = Ints.toByteArray(i);
			rng.nextBytes(valueBytes);

			LedgerIndex keyIndex = new LedgerIndex(MockAtomContent.GENERIC_KEY_PREFIX, Longs.toByteArray(key));
			MockAtom atom = new MockAtom(new MockAtomContent(keyIndex, valueBytes));
			mockApplication.queue(atom);
		}
	}
}