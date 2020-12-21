/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 */

package com.radixdlt.sanitytestsuite.scenario.keyverify;

import com.radixdlt.sanitytestsuite.model.SanityTestVector;

import static com.radixdlt.sanitytestsuite.scenario.keyverify.KeyVerifyTestVector.Expected;
import static com.radixdlt.sanitytestsuite.scenario.keyverify.KeyVerifyTestVector.Input;

// CHECKSTYLE:OFF checkstyle:VisibilityModifier
public final class KeyVerifyTestVector extends SanityTestVector<Input, Expected> {
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
}
// CHECKSTYLE:ON checkstyle:VisibilityModifier