package com.radixdlt.serialization;

import org.radix.atoms.AtomStoreBloom;

/**
 * Check serialization of AtomStoreBloom
 */
public class AtomStoreBloomSerializeTest extends SerializeObject<AtomStoreBloom> {
	public AtomStoreBloomSerializeTest() {
		super(AtomStoreBloom.class, AtomStoreBloomSerializeTest::get);
	}

	private static AtomStoreBloom get() {
		return new AtomStoreBloom(0.1, 100, "test");
	}
}
