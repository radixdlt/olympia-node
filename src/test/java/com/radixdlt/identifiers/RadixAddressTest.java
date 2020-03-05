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

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.Test;

public class RadixAddressTest {

	@Test
	public void equalsContract() {
		EqualsVerifier.forClass(RadixAddress.class)
				.withIgnoredFields("key") // other field(s) dependent on `key` is used
				.withIgnoredFields("base58") // other field(s) dependent on `base58` is used
				.verify();
	}

	@Test
	public void when_an_address_is_created_with_same_string__they_should_be_equal_and_have_same_hashcode() {
		RadixAddress address0 = RadixAddress.from("JH1P8f3znbyrDj8F4RWpix7hRkgxqHjdW2fNnKpR3v6ufXnknor");
		RadixAddress address1 = RadixAddress.from(address0.toString());
		assertThat(address0).isEqualTo(address1);
		assertThat(address0).hasSameHashCodeAs(address1);
	}
}