package org.radix.universe.system;

import java.lang.reflect.Field;
import java.util.function.LongSupplier;

import org.junit.BeforeClass;

import com.radixdlt.crypto.CryptoException;
import org.radix.serialization.SerializeMessageObject;
import org.radix.shards.ShardSpace;

/**
 * Check serialization of org.radix.universe.system.LocalSystem
 */
public class LocalSystemSerializeTest extends SerializeMessageObject<LocalSystem> {
	@BeforeClass
	public static void beforeLocalSystemSerializeTest() throws Exception {
		// Make sure serialized LocalSystem class is predictable
		Field freeMemory = LocalSystem.class.getDeclaredField("freeMemory");
		freeMemory.setAccessible(true);
		LongSupplier fixedNumber = () -> 12345678L;
		freeMemory.set(null, fixedNumber);

		Field maxMemory = LocalSystem.class.getDeclaredField("maxMemory");
		maxMemory.setAccessible(true);
		LongSupplier maxNumber = () -> 12345678L;
		maxMemory.set(null, maxNumber);

		Field totalMemory = LocalSystem.class.getDeclaredField("totalMemory");
		totalMemory.setAccessible(true);
		LongSupplier totalNumber = () -> 12345678L;
		totalMemory.set(null, totalNumber);
	}

	public LocalSystemSerializeTest() {
		super(LocalSystem.class, LocalSystemSerializeTest::getSystem);
	}

	private static LocalSystem getSystem() {
		LocalSystem newSystem;
		try {
			newSystem = new LocalSystem();
			newSystem.setPlanck(101);
			newSystem.setShards(new ShardSpace(10000, 20000));
			return newSystem;
		} catch (CryptoException e) {
			throw new IllegalStateException(e);
		}
	}
}
