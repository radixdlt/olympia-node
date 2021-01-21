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

package com.radixdlt.network.addressbook;

import com.google.common.hash.HashCode;
import com.radixdlt.crypto.HashUtils;
import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;
import org.junit.Before;
import org.junit.Test;

import com.google.common.collect.ImmutableList;
import com.radixdlt.identifiers.EUID;
import com.radixdlt.network.transport.StaticTransportMetadata;
import com.radixdlt.network.transport.TransportException;
import com.radixdlt.network.transport.TransportInfo;

import static org.junit.Assert.*;
import static org.assertj.core.api.Assertions.assertThat;

public class PeerWithTransportTest {

	private TransportInfo dummy;
	private PeerWithTransport pwt;

	@Before
	public void setUp() {
		dummy = TransportInfo.of("DUMMY", StaticTransportMetadata.empty());
		pwt = new PeerWithTransport(dummy);
	}

	@Test
	public void testToString() {
		String s = this.pwt.toString();

		assertThat(s)
			.contains("PeerWithTransport") // class name
			.contains(this.dummy.name()); // transport name
	}

	@Test
	public void testGetNID() {
		assertThat(this.pwt.getNID()).isEqualTo(EUID.ZERO);
	}

	@Test
	public void testHasNID() {
		assertThat(this.pwt.hasNID()).isFalse();
	}

	@Test
	public void testSupportsTransport() {
		assertThat(this.pwt.supportsTransport("NOTEXIST")).isFalse();
		assertThat(this.pwt.supportsTransport(this.dummy.name())).isTrue();
	}

	@Test
	public void testSupportedTransports() {
		ImmutableList<TransportInfo> tis = this.pwt.supportedTransports().collect(ImmutableList.toImmutableList());
		assertThat(tis).contains(this.dummy);
	}

	@Test
	public void testConnectionData() {
		assertThat(this.pwt.connectionData("DUMMY")).isEqualTo(this.dummy.metadata());
	}

	@Test(expected = TransportException.class)
	public void testConnectionDataThrows() {
		this.pwt.connectionData("NONESUCH");
		fail();
	}

	@Test
	public void testHasSystem() {
		assertThat(this.pwt.hasSystem()).isFalse();
	}

	@Test
	public void testGetSystem() {
		assertThat(this.pwt.getSystem()).isNull();
	}

	@Test
	public void equalsContract() {
		EqualsVerifier.forClass(PeerWithTransport.class)
				.withIgnoredFields("banReason", "timestamps")
				.suppress(Warning.NONFINAL_FIELDS)
				.withPrefabValues(HashCode.class, HashUtils.random256(), HashUtils.random256())
				.verify();
	}
}
