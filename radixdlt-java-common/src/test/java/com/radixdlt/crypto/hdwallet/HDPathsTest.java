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

package com.radixdlt.crypto.hdwallet;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class HDPathsTest {

	@Test
	public void when_creating_an_hdpath_from_only_a_slash_then_a_path_with_depth_0_is_created() throws HDPathException {
		HDPath hdPath = DefaultHDPath.of("/");
		assertEquals("M", hdPath.toString());
		assertEquals(0, hdPath.depth());
	}

	@Test
	public void when_creating_an_hdpath_from_only_an_empty_string_then_a_path_with_depth_0_is_created() throws HDPathException {
		HDPath hdPath = DefaultHDPath.of("");
		assertEquals("M", hdPath.toString());
		assertEquals(0, hdPath.depth());
	}

	@Test
	public void when_validating_valid_bip32_paths_then_they_all_pass_validation() {
		assertValid("");
		assertValid("/");
		assertValid("m/0");
		assertValid("m/1");
		assertValid("m/2");
		assertValid("m/0'");
		assertValid("m/1'");
		assertValid("m/2'");
		assertValid("m/2147483646");
		assertValid("m/2147483646'");

		assertValid("m/44'/0");
		assertValid("m/44'/0'");
		assertValid("m/44'/1");
		assertValid("m/44'/1'");
		assertValid("m/44'/2");
		assertValid("m/44'/2'");
		assertValid("m/44'/2147483646");
		assertValid("m/44'/2147483646'");

		assertValid("m/44'/536'/2'/1/3");

		assertValid("m/1/2/3/4/5/6/7/8/");

		assertValid("M/1/2");
		assertValid("M/1'/2'");
	}

	@Test
	public void when_validating_invalid_bip32_paths_none_passes_validation() {
		assertInvalid("invalid");
		assertInvalid("z");
		assertInvalid("m/");
		assertInvalid("/m");
		assertInvalid(" ");
		assertInvalid("/ ");
		assertInvalid(" /");
		assertInvalid(" m/0");
		assertInvalid("m/m");
		assertInvalid("m/x");
		assertInvalid("m//0");
		assertInvalid("m/0/1//");

		assertInvalid("m/44'/536' / 2' / 1/3");

		assertInvalid("m/44H/0H");

		assertInvalid("m/44'/9999999999999999999999");
		assertInvalid("m/9999999999999999999999");
		assertInvalid("m/44'/-1");
		assertInvalid("m/-1");

		assertInvalid("m/44'/536'/2'/1/-3");
		assertInvalid("m/44'/536'/2'/1/-3'");
		assertInvalid("m/44'/536'/2'/1/-999999999999999999");
		assertInvalid("m/44'/536'/2'/1/-999999999999999999'");
	}

	@Test
	public void when_deriving_the_next_bip32_path_of_a_valid_path_then_the_correct_one_is_returned() {
		assertNextPath("m/1", "m/0");
		assertNextPath("m/1'", "m/0'");
		assertNextPath("m/2'", "m/1'");
		assertNextPath("m/2147483647", "m/2147483646");
		assertNextPath("m/2147483647'", "m/2147483646'");

		assertNextPath("m/44'/1", "m/44'/0");
		assertNextPath("m/44'/1'", "m/44'/0'");
		assertNextPath("m/44'/2'", "m/44'/1'");
		assertNextPath("m/44'/2147483647", "m/44'/2147483646");
		assertNextPath("m/44'/2147483647'", "m/44'/2147483646'");
	}

	@Test
	public void when_deriving_a_radix_bip44_key_path_then_no_error_is_thrown() throws HDPathException {
		assertNoThrowCreatingHDPathFrom("m/44'/536'/2'/1/3");
	}

	private void assertValid(String path) {
		assertTrue(HDPaths.isValidHDPath(path));
	}

	private void assertInvalid(String path) {
		assertFalse(HDPaths.isValidHDPath(path));
		assertErrorIsThrownCreatingHDPathFrom(path);
	}

	private void assertErrorIsThrownCreatingHDPathFrom(String path) {
		assertThatThrownBy(() -> DefaultHDPath.of(path))
				.isInstanceOf(HDPathException.class);
	}


	private void assertNoThrowCreatingHDPathFrom(String path) throws HDPathException {
		assertNotNull(DefaultHDPath.of(path));
	}

	private void assertNextPath(String expected, String path) {
		try {
			String nextPath = BIP32Path.fromString(path).next().toString();
			assertEquals(expected, nextPath);
		} catch (Exception e) {
			fail("Expected to be able to get next path, but got exception" + e);
		}
	}
}
