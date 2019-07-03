package org.radix.universe.system;

import com.radixdlt.serialization.SerializeMessageObject;
import org.radix.shards.ShardSpace;

/**
 * Check serialization of org.radix.universe.system.System
 */
public class SystemSerializeTest extends SerializeMessageObject<RadixSystem> {
	public SystemSerializeTest() {
		super(RadixSystem.class, SystemSerializeTest::getSystem);
	}

	private static RadixSystem getSystem() {
		RadixSystem newSystem = new RadixSystem();
		newSystem.setPlanck(101);
		newSystem.setPort(30000);
		newSystem.setShards(new ShardSpace(10000, 20000));
		return newSystem;
	}
}
