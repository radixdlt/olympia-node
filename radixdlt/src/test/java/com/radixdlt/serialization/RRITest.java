package com.radixdlt.serialization;

import com.radixdlt.atomos.RadixAddress;
import com.radixdlt.atomos.RRI;

public class RRITest extends SerializeValue<RRI> {
	public RRITest() {
		super(RRI.class, RRITest::get);
	}

	private static RRI get() {
		return RRI.of(
			RadixAddress.from("JH1P8f3znbyrDj8F4RWpix7hRkgxqHjdW2fNnKpR3v6ufXnknor"),
			"JOSH"
		);
	}
}
