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

package com.radixdlt.sanitytestsuite.scenario.hashing;

import com.radixdlt.sanitytestsuite.scenario.SanityTestScenarioRunner;
import com.radixdlt.utils.Bytes;

import static org.junit.Assert.assertEquals;

public final class HashingTestScenarioRunner extends SanityTestScenarioRunner<HashingTestVector> {
	public String testScenarioIdentifier() {
		return "hashing";
	}

	@Override
	public Class<HashingTestVector> testVectorType() {
		return HashingTestVector.class;
	}

	public void doRunTestVector(HashingTestVector testVector) throws AssertionError {
		var hashHex = Bytes.toHexString(sha256Hash(testVector.input.bytesToHash()));

		assertEquals(testVector.expected.hash, hashHex);
	}
}
