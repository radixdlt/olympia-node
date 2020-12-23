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

import org.junit.Test;

import static org.junit.Assert.*;
import static org.hamcrest.CoreMatchers.*;

import nl.jqno.equalsverifier.EqualsVerifier;

public class TransportInfoTest {

	@Test
	public void equalsContract() {
	    EqualsVerifier.forClass(TransportInfo.class).verify();
	}

	@Test
	public void testOf() {
		StaticTransportMetadata stm = StaticTransportMetadata.of("a", "b");
		TransportInfo info = TransportInfo.of("TEST", stm);

		assertThat(info.name(), equalTo("TEST"));
		assertThat(info.metadata(), equalTo(stm));
	}

	@Test
	public void testToString() {
		StaticTransportMetadata stm = StaticTransportMetadata.of("a", "b");
		TransportInfo info = TransportInfo.of("TEST", stm);

		assertThat(info.toString(), containsString("TEST"));
		assertThat(info.toString(), containsString("a=b"));
	}
}
