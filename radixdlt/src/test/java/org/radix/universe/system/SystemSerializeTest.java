package org.radix.universe.system;

import org.radix.serialization.SerializeMessageObject;

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
		return newSystem;
	}
}
