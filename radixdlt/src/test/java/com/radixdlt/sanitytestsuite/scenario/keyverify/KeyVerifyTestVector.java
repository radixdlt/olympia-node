package com.radixdlt.sanitytestsuite.scenario.keyverify;

import com.radixdlt.sanitytestsuite.model.SanityTestVector;

public final class KeyVerifyTestVector implements SanityTestVector {
	public static final class Input {
		public String comment;
		public int wycheProofVectorId;
		public String msg;
		public String publicKeyUncompressed;
		public String signatureDerEncoded;
	}
	public static final class Expected {
		public boolean isValid;
	}
	public Expected expected;
	public Input input;
}