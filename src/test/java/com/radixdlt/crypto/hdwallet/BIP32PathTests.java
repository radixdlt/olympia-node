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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class BIP32PathTests {

	@Test
	public void test_valid_paths() {
		assert_valid_path("");
		assert_valid_path("/");
		assert_valid_path("m/0");
		assert_valid_path("m/1");
		assert_valid_path("m/2");
		assert_valid_path("m/0'");
		assert_valid_path("m/1'");
		assert_valid_path("m/2'");
		assert_valid_path("m/2147483646");
		assert_valid_path("m/2147483646'");

		assert_valid_path("m/44'/0");
		assert_valid_path("m/44'/0'");
		assert_valid_path("m/44'/1");
		assert_valid_path("m/44'/1'");
		assert_valid_path("m/44'/2");
		assert_valid_path("m/44'/2'");
		assert_valid_path("m/44'/2147483646");
		assert_valid_path("m/44'/2147483646'");

		assert_valid_path("m/44'/536'/2'/1/3");

		assert_valid_path("m/1/2/3/4/5/6/7/8/");

		assert_valid_path("M/1/2");
		assert_valid_path("M/1'/2'");
	}

	@Test
	public void test_creation_of_hd_path() {
		assert_no_throw_create_hdpath_from_string("m/44'/536'/2'/1/3");
	}


	@Test
	public void test_invalid_paths() {
		assert_invalid_path("invalid");
		assert_invalid_path("z");
		assert_invalid_path("m/");
		assert_invalid_path("/m");
		assert_invalid_path(" ");
		assert_invalid_path("/ ");
		assert_invalid_path(" /");
		assert_invalid_path(" m/0");
		assert_invalid_path("m/m");
		assert_invalid_path("m/x");
		assert_invalid_path("m//0");
		assert_invalid_path("m/0/1//");

		assert_invalid_path("m/44'/536' / 2' / 1/3");

		assert_invalid_path("m/44H/0H");

		assert_invalid_path("m/44'/9999999999999999999999");
		assert_invalid_path("m/44'/-1");
	}

	@Test
	public void test_next_path() {
		assertEquals("m/1", nextPath("m/0"));
		assertEquals("m/1'", nextPath("m/0'"));
		assertEquals("m/2'", nextPath("m/1'"));
		assertEquals("m/2147483647", nextPath("m/2147483646"));
		assertEquals("m/2147483647'", nextPath("m/2147483646'"));

		assertEquals("m/44'/1", nextPath("m/44'/0"));
		assertEquals("m/44'/1'", nextPath("m/44'/0'"));
		assertEquals("m/44'/2'", nextPath("m/44'/1'"));
		assertEquals("m/44'/2147483647", nextPath("m/44'/2147483646"));
		assertEquals("m/44'/2147483647'", nextPath("m/44'/2147483646'"));
	}

	private void assert_valid_path(String path) {
		assertTrue(HDPaths.validateBIP32Path(path));
	}

	private void assert_invalid_path(String path) {
		assertFalse(HDPaths.validateBIP32Path(path));
		assert_throw_create_hdpath_from_string(path);
	}

	private void assert_throw_create_hdpath_from_string(String path) {
		assertThatThrownBy(() -> DefaultHDPath.of(path))
				.isInstanceOf(HDPathException.class);
	}


	private void assert_no_throw_create_hdpath_from_string(String path) {
		try {
			DefaultHDPath.of(path);
		} catch (HDPathException e) {
			fail("Expected no errors thrown");
		}
	}

	private String nextPath(String path) {
		try {
			return BIP32Path.fromString(path)
					.next().toString();
		} catch (Exception e) {
			return "";
		}
	}
}
