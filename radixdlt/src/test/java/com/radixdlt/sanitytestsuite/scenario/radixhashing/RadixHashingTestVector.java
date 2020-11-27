package com.radixdlt.sanitytestsuite.scenario.radixhashing;

import com.radixdlt.sanitytestsuite.model.SanityTestVector;

import java.nio.charset.StandardCharsets;

// CHECKSTYLE:OFF checkstyle:VisibilityModifier
public final class RadixHashingTestVector implements SanityTestVector {
	public static final class Expected {
		public String hashOfHash;
	}

	public static final class Input {
		private String stringToHash;
		public byte[] bytesToHash() {
			return this.stringToHash.getBytes(StandardCharsets.UTF_8);
		}
	}

	public Expected expected;
	public Input input;
}
// CHECKSTYLE:ON checkstyle:VisibilityModifier