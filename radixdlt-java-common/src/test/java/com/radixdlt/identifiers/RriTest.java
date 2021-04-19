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

public class RriTest {
	@BeforeClass
	public static void setup() {
		TestSetupUtils.installBouncyCastleProvider();
	}

	@Test
	public void equalsContract() {
		EqualsVerifier.forClass(Rri.class)
				.verify();
	}

	@Test
	public void when_parsing_a_correctly_formed_rri__exception_is_not_thrown() {
		List<String> correctRRIs = Arrays.asList(
			"xrd_rr1gd5j68"
		);

		correctRRIs.forEach(rriStr -> assertThat(Rri.fromBech32(rriStr)).isNotNull());
	}

	@Test
	public void when_parsing_bad_structure__illegal_argument_exception_should_occur() {
		List<String> badTypeRRIs = Arrays.asList(
			"xrd",
			"xrd1l4hlf5",
			"test1qfthlphlwt6gx22p27a7223h040jtkp9pr3atqeqrm52h0hmwcaqkkkp5s6"
		);

		badTypeRRIs.forEach(rriStr ->
			assertThatThrownBy(() -> Rri.fromBech32(rriStr))
				.isInstanceOf(IllegalArgumentException.class)
		);
	}
}