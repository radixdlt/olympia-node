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

import java.util.stream.Stream;

import com.google.common.hash.HashCode;
import com.radixdlt.crypto.HashUtils;
import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;
import org.junit.Before;
import org.junit.Test;
import org.radix.universe.system.RadixSystem;

import com.google.common.collect.ImmutableList;
import com.radixdlt.identifiers.EUID;
import com.radixdlt.network.transport.StaticTransportMetadata;
import com.radixdlt.network.transport.TransportException;
import com.radixdlt.network.transport.TransportInfo;
import com.radixdlt.network.transport.udp.UDPConstants;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.assertThat;

public class PeerWithSystemTest {

	private EUID nid;
	private TransportInfo dummy;
	private RadixSystem system;
	private PeerWithSystem pws;

	@Before
	public void setUp() {
		this.nid = EUID.ONE;
		this.dummy = TransportInfo.of("DUMMY", StaticTransportMetadata.empty());
		this.system = mock(RadixSystem.class);
		when(this.system.supportedTransports()).thenAnswer(invocation -> Stream.of(this.dummy));
		when(this.system.getNID()).thenReturn(this.nid);
		this.pws = new PeerWithSystem(this.system);
	}

	@Test
	public void testToString() {
		String s = this.pws.toString();

		assertThat(s).contains("PeerWithSystem"); // class name
		assertThat(s).contains(this.nid.toString()); // nid
		assertThat(s).contains("{}");

		EUID localNid = EUID.TWO;
		TransportInfo fakeUdp = TransportInfo.of(UDPConstants.NAME,
			StaticTransportMetadata.of(
				UDPConstants.METADATA_HOST, "127.0.0.1",
				UDPConstants.METADATA_PORT, "10000"
			)
		);
		RadixSystem localSystem = mock(RadixSystem.class);
		when(localSystem.supportedTransports()).thenAnswer(invocation -> Stream.of(fakeUdp));
		when(localSystem.getNID()).thenReturn(localNid);
		PeerWithSystem localPws = new PeerWithSystem(localSystem);
		String s2 = localPws.toString();
		assertThat(s2).contains("PeerWithSystem"); // class name
		assertThat(s2).contains(localNid.toString()); // nid
		assertThat(s2).contains(fakeUdp.metadata().get(UDPConstants.METADATA_HOST));
		assertThat(s2).contains(fakeUdp.metadata().get(UDPConstants.METADATA_PORT));
	}

	@Test
	public void testGetNID() {
		assertThat(this.pws.getNID()).isEqualTo(this.nid);
	}

	@Test
	public void testHasNID() {
		assertThat(this.pws.hasNID()).isTrue();
	}

	@Test
	public void testSupportsTransport() {
		assertThat(this.pws.supportsTransport("NONESUCH")).isFalse();
		assertThat(this.pws.supportsTransport(this.dummy.name())).isTrue();
	}

	@Test
	public void testSupportedTransports() {
		ImmutableList<TransportInfo> tis = this.pws.supportedTransports().collect(ImmutableList.toImmutableList());
		assertThat(tis).contains(this.dummy);
	}

	@Test
	public void testConnectionData() {
		assertThat(this.pws.connectionData(this.dummy.name())).isEqualTo(this.dummy.metadata());
	}

	@Test(expected = TransportException.class)
	public void testConnectionDataThrows() {
		this.pws.connectionData("ANY");
		fail();
	}

	@Test
	public void testHasSystem() {
		assertThat(this.pws.hasSystem()).isTrue();
	}

	@Test
	public void testGetSystem() {
		assertThat(this.pws.getSystem()).isSameAs(this.system);
	}

	@Test
	public void equalsContract() {
		EqualsVerifier.forClass(PeerWithSystem.class)
				.withIgnoredFields("banReason", "timestamps")
				.suppress(Warning.NONFINAL_FIELDS)
				.withPrefabValues(HashCode.class, HashUtils.random256(), HashUtils.random256())
				.verify();
	}
}
