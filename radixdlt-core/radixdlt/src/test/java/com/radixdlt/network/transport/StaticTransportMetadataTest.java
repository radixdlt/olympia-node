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

package com.radixdlt.network.transport;

import java.util.HashMap;
import java.util.Map;
import org.junit.Test;

import com.google.common.collect.ImmutableMap;

import static org.junit.Assert.*;
import static org.hamcrest.CoreMatchers.*;

import nl.jqno.equalsverifier.EqualsVerifier;

public class StaticTransportMetadataTest {

	@Test
	public void equalsContract() {
	    EqualsVerifier.forClass(StaticTransportMetadata.class).verify();
	}

	@Test
	public void testEmpty() {
		StaticTransportMetadata stm = StaticTransportMetadata.empty();

		assertThat(stm.toString(), containsString("{}")); // The empty map
	}

	@Test
	public void testFromMap() {
		Map<String, String> metadata = new HashMap<>();
		metadata.put("a", "b");
		StaticTransportMetadata stm = StaticTransportMetadata.from(metadata);

		assertThat(stm.get("a"), equalTo("b"));
		assertThat(stm.get("b"), nullValue());
	}

	@Test
	public void testFromImmutableMap() {
		StaticTransportMetadata stm = StaticTransportMetadata.from(
			ImmutableMap.of("a", "b")
		);

		assertThat(stm.get("a"), equalTo("b"));
		assertThat(stm.get("b"), nullValue());
	}

	@Test
	public void testOfTwoArgs() {
		StaticTransportMetadata stm = StaticTransportMetadata.of("a", "b");

		assertThat(stm.get("a"), equalTo("b"));
		assertThat(stm.get("b"), nullValue());
	}

	@Test
	public void testOfFourArgs() {
		StaticTransportMetadata stm = StaticTransportMetadata.of("a", "b", "c", "d");

		assertThat(stm.get("a"), equalTo("b"));
		assertThat(stm.get("c"), equalTo("d"));
		assertThat(stm.get("e"), nullValue());
	}

	@Test
	public void testToString() {
		StaticTransportMetadata stm = StaticTransportMetadata.of("a", "b", "c", "d");

		assertThat(stm.toString(), containsString("a=b"));
		assertThat(stm.toString(), containsString("c=d"));
	}
}
