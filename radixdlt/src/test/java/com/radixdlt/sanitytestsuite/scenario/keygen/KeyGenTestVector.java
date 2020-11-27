package com.radixdlt.sanitytestsuite.scenario.keygen;

import com.radixdlt.sanitytestsuite.model.SanityTestVector;

public final class KeyGenTestVector implements SanityTestVector {
	public static final class Expected {
		public  String uncompressedPublicKey;
	}

	public static final class Input  {
		public  String privateKey;
	}

	public  Expected expected;
	public Input input;
}