/*
 *
 *  * (C) Copyright 2020 Radix DLT Ltd
 *  *
 *  * Radix DLT Ltd licenses this file to you under the Apache License,
 *  * Version 2.0 (the "License"); you may not use this file except in
 *  * compliance with the License.  You may obtain a copy of the
 *  * License at
 *  *
 *  *  http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing,
 *  * software distributed under the License is distributed on an
 *  * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 *  * either express or implied.  See the License for the specific
 *  * language governing permissions and limitations under the License.
 *
 */

package com.radixdlt.crypto.hdwallet;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.Assert.assertEquals;

public class BIP32PathTests {

	@Test
	public void test_next_path() {
		assertEquals("m/1", BIP32Path.pathOfKeySubsequentToPath("m/0"));
		assertEquals("m/1'", BIP32Path.pathOfKeySubsequentToPath("m/0'"));
		assertEquals("m/2'", BIP32Path.pathOfKeySubsequentToPath("m/1'"));
		assertEquals("m/2147483647", BIP32Path.pathOfKeySubsequentToPath("m/2147483646"));
		assertEquals("m/2147483647'", BIP32Path.pathOfKeySubsequentToPath("m/2147483646'"));

		assertEquals("m/44'/1", BIP32Path.pathOfKeySubsequentToPath("m/44'/0"));
		assertEquals("m/44'/1'", BIP32Path.pathOfKeySubsequentToPath("m/44'/0'"));
		assertEquals("m/44'/2'", BIP32Path.pathOfKeySubsequentToPath("m/44'/1'"));
		assertEquals("m/44'/2147483647", BIP32Path.pathOfKeySubsequentToPath("m/44'/2147483646"));
		assertEquals("m/44'/2147483647'", BIP32Path.pathOfKeySubsequentToPath("m/44'/2147483646'"));
	}

	@Test
	public void test_invalid_characters() {
		assert_invalid_path("invalid");
	}

	private void assert_invalid_path(String path) {
		assertThatThrownBy(() -> DefaultHDPath.of(path))
				.isInstanceOf(HDPathException.class);
	}
}
