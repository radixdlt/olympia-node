package com.radixdlt.sanitytestsuite.scenario.hashing;


import com.radixdlt.sanitytestsuite.model.SanityTestVector;

import java.nio.charset.StandardCharsets;

// CHECKSTYLE:OFF checkstyle:VisibilityModifier
public final class HashingTestVector implements SanityTestVector {

	static final class Expected {
		public String hash;
	}

	static final class Input {
		private String stringToHash;
		public byte[] bytesToHash() {
			return this.stringToHash.getBytes(StandardCharsets.UTF_8);
		}
	}

	public Expected expected;
	public Input input;
}
// CHECKSTYLE:ON checkstyle:VisibilityModifier