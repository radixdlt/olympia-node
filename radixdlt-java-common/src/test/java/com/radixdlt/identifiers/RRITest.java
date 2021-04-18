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

package com.radixdlt.identifiers;

import com.radixdlt.TestSetupUtils;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class RRITest {
	@BeforeClass
	public static void setup() {
		TestSetupUtils.installBouncyCastleProvider();
	}

	@Test
	public void equalsContract() {
		EqualsVerifier.forClass(RRI.class)
				.verify();
	}

	@Test
	public void when_parsing_a_correctly_formed_rri__exception_is_not_thrown() {
		List<String> correctRRIs = Arrays.asList(
			"xrd",
			"JH1P8f3znbyrDj8F4RWpix7hRkgxqHjdW2fNnKpR3v6ufXnknor.name",
			"JH1P8f3znbyrDj8F4RWpix7hRkgxqHjdW2fNnKpR3v6ufXnknor.nam",
			"JH1P8f3znbyrDj8F4RWpix7hRkgxqHjdW2fNnKpR3v6ufXnknor.n"
		);

		correctRRIs.forEach(rriStr -> assertThat(RRI.from(rriStr)).isNotNull());
	}

	@Test
	public void when_parsing_bad_structure__illegal_argument_exception_should_occur() {
		List<String> badTypeRRIs = Arrays.asList(
			"a.JH1P8f3znbyrDj8F4RWpix7hRkgxqHjdW2fNnKpR3v6ufXnknor.type.name",
			"JH1P8f3znbyrDj8F4RWpix7hRkgxqHjdW2fNnKpR3v6ufXnknor.type.",
			"JH1P8f3znbyrDj8F4RWpix7hRkgxqHjdW2fNnKpR3v6ufXnknor.."
		);

		badTypeRRIs.forEach(rriStr ->
			assertThatThrownBy(() -> RRI.from(rriStr))
				.isInstanceOf(IllegalArgumentException.class)
		);
	}


	@Test
	public void when_parsing_bad_type__illegal_argument_exception_should_occur() {
		List<String> badTypeRRIs = Arrays.asList(
			".JH1P8f3znbyrDj8F4RWpix7hRkgxqHjdW2fNnKpR3v6ufXnknor.name",
			"JH1P8f3znbyrDj8F4RWpix7hRkgxqHjdW2fNnKpR3v6ufXnknor. .name",
			"JH1P8f3znbyrDj8F4RWpix7hRkgxqHjdW2fNnKpR3v6ufXnknor. type.NAME",
			"JH1P8f3znbyrDj8F4RWpix7hRkgxqHjdW2fNnKpR3v6ufXnknor._type.name123456",
			"JH1P8f3znbyrDj8F4RWpix7hRkgxqHjdW2fNnKpR3v6ufXnknor.:e.1",
			"JH1P8f3znbyrDj8F4RWpix7hRkgxqHjdW2fNnKpR3v6ufXnknor..1"
		);

		badTypeRRIs.forEach(rriStr ->
			assertThatThrownBy(() -> RRI.from(rriStr))
				.isInstanceOf(IllegalArgumentException.class)
		);
	}

	@Test
	public void when_parsing_bad_name__illegal_argument_exception_should_occur() {
		List<String> badTypeRRIs = Arrays.asList(
			"JH1P8f3znbyrDj8F4RWpix7hRkgxqHjdW2fNnKpR3v6ufXnknor.type.#",
			"JH1P8f3znbyrDj8F4RWpix7hRkgxqHjdW2fNnKpR3v6ufXnknor.type.a b",
			"JH1P8f3znbyrDj8F4RWpix7hRkgxqHjdW2fNnKpR3v6ufXnknor.type.*",
			"JH1P8f3znbyrDj8F4RWpix7hRkgxqHjdW2fNnKpR3v6ufXnknor.type. name",
			"JH1P8f3znbyrDj8F4RWpix7hRkgxqHjdW2fNnKpR3v6ufXnknor.type. ",
			"JH1P8f3znbyrDj8F4RWpix7hRkgxqHjdW2fNnKpR3v6ufXnknor.type."
		);

		badTypeRRIs.forEach(rriStr ->
			assertThatThrownBy(() -> RRI.from(rriStr))
				.isInstanceOf(IllegalArgumentException.class)
		);
	}
}