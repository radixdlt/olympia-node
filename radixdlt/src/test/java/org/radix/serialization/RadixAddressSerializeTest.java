package org.radix.serialization;

import com.radixdlt.atomos.RadixAddress;

public class RadixAddressSerializeTest extends SerializeValue<RadixAddress> {
	public RadixAddressSerializeTest() {
		super(RadixAddress.class, RadixAddressSerializeTest::get);
	}

	private static RadixAddress get() {
		return RadixAddress.from("JH1P8f3znbyrDj8F4RWpix7hRkgxqHjdW2fNnKpR3v6ufXnknor");
	}
}
