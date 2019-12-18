package org.radix.universe.system;

import com.radixdlt.crypto.CryptoException;
import org.radix.serialization.SerializeMessageObject;

/**
 * Check serialization of org.radix.universe.system.LocalSystem
 */
public class LocalSystemSerializeTest extends SerializeMessageObject<LocalSystem> {
	public LocalSystemSerializeTest() {
		super(LocalSystem.class, LocalSystemSerializeTest::getSystem);
	}

	private static LocalSystem getSystem() {
		LocalSystem newSystem;
		try {
			newSystem = new LocalSystem();
			newSystem.setPlanck(101);
			return newSystem;
		} catch (CryptoException e) {
			throw new IllegalStateException(e);
		}
	}
}
