/* Copyright 2021 Radix DLT Ltd incorporated in England.
 *
 * Licensed under the Radix License, Version 1.0 (the "License"); you may not use this
 * file except in compliance with the License. You may obtain a copy of the License at:
 *
 * radixfoundation.org/licenses/LICENSE-v1
 *
 * The Licensor hereby grants permission for the Canonical version of the Work to be
 * published, distributed and used under or by reference to the Licensor’s trademark
 * Radix ® and use of any unregistered trade names, logos or get-up.
 *
 * The Licensor provides the Work (and each Contributor provides its Contributions) on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied,
 * including, without limitation, any warranties or conditions of TITLE, NON-INFRINGEMENT,
 * MERCHANTABILITY, or FITNESS FOR A PARTICULAR PURPOSE.
 *
 * Whilst the Work is capable of being deployed, used and adopted (instantiated) to create
 * a distributed ledger it is your responsibility to test and validate the code, together
 * with all logic and performance of that code under all foreseeable scenarios.
 *
 * The Licensor does not make or purport to make and hereby excludes liability for all
 * and any representation, warranty or undertaking in any form whatsoever, whether express
 * or implied, to any entity or person, including any representation, warranty or
 * undertaking, as to the functionality security use, value or other characteristics of
 * any distributed ledger nor in respect the functioning or value of any tokens which may
 * be created stored or transferred using the Work. The Licensor does not warrant that the
 * Work or any use of the Work complies with any law or regulation in any territory where
 * it may be implemented or used or that it will be appropriate for any specific purpose.
 *
 * Neither the licensor nor any current or former employees, officers, directors, partners,
 * trustees, representatives, agents, advisors, contractors, or volunteers of the Licensor
 * shall be liable for any direct or indirect, special, incidental, consequential or other
 * losses of any kind, in tort, contract or otherwise (including but not limited to loss
 * of revenue, income or profits, or loss of use or data, or loss of reputation, or loss
 * of any economic or other opportunity of whatsoever nature or howsoever arising), arising
 * out of or in connection with (without limitation of any use, misuse, of any ledger system
 * or use made or its functionality or any performance or operation of any code or protocol
 * caused by bugs or programming or logic errors or otherwise);
 *
 * A. any offer, purchase, holding, use, sale, exchange or transmission of any
 * cryptographic keys, tokens or assets created, exchanged, stored or arising from any
 * interaction with the Work;
 *
 * B. any failure in a transmission or loss of any token or assets keys or other digital
 * artefacts due to errors in transmission;
 *
 * C. bugs, hacks, logic errors or faults in the Work or any communication;
 *
 * D. system software or apparatus including but not limited to losses caused by errors
 * in holding or transmitting tokens by any third-party;
 *
 * E. breaches or failure of security including hacker attacks, loss or disclosure of
 * password, loss of private key, unauthorised use or misuse of such passwords or keys;
 *
 * F. any losses including loss of anticipated savings or other benefits resulting from
 * use of the Work or any changes to the Work (however implemented).
 *
 * You are solely responsible for; testing, validating and evaluation of all operation
 * logic, functionality, security and appropriateness of using the Work for any commercial
 * or non-commercial purpose and for any reproduction or redistribution by You of the
 * Work. You assume all risks associated with Your use of the Work and the exercise of
 * permissions under this License.
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
