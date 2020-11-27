package com.radixdlt.sanitytestsuite.scenario.keysign;

import com.radixdlt.sanitytestsuite.model.SanityTestVector;

// CHECKSTYLE:OFF checkstyle:VisibilityModifier
public final class KeySignTestVector implements SanityTestVector {
	public static final class Input {
		public String privateKey;
		public String messageToSign;
	}
	public static final class Expected {
		public static final class Signature {
			public String r;
			public String s;
			public String der;
		}
		public String k;
		public Signature signature;
	}

	public Expected expected;
	public Input input;
}
// CHECKSTYLE:ON checkstyle:VisibilityModifier