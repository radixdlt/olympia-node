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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.radixdlt.identifiers.EUID;
import com.radixdlt.network.transport.StaticTransportMetadata;
import com.radixdlt.network.transport.TransportInfo;
import com.radixdlt.network.transport.udp.UDPConstants;
import org.junit.Before;
import org.junit.Test;
import org.radix.Radix;
import org.radix.time.Time;
import org.radix.time.Timestamps;
import org.radix.universe.system.RadixSystem;

import java.util.stream.Stream;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class StandardFiltersTest {
	private EUID nidPwn;

	private PeerWithNid pwn;
	private PeerWithSystem pws;
	private PeerWithTransport pwt;

	@Before
	public void setUp() {
		this.nidPwn = EUID.ONE;
		EUID nidPws = EUID.TWO;
		TransportInfo transportInfo = TransportInfo.of(UDPConstants.NAME,
			StaticTransportMetadata.of(
				UDPConstants.METADATA_HOST, "127.0.0.1",
				UDPConstants.METADATA_PORT, "10000"
			)
		);
		RadixSystem system = mock(RadixSystem.class);
		when(system.supportedTransports()).thenAnswer(invocation -> Stream.of(transportInfo));
		when(system.getNID()).thenReturn(nidPws);
		when(system.getAgentVersion()).thenReturn(Radix.MAJOR_AGENT_VERSION);

		this.pwn = new PeerWithNid(this.nidPwn);
		this.pws = new PeerWithSystem(system);
		this.pwt = new PeerWithTransport(transportInfo);
	}

	@Test
	public void testHasTransports() {
		assertTrue(StandardFilters.hasTransports().test(this.pwt));
		assertFalse(StandardFilters.hasTransports().test(this.pwn));
	}

	@Test
	public void testNotBanned() {
		pwt.ban("No good reason at all");
		assertFalse(StandardFilters.notBanned().test(this.pwt));
		assertTrue(StandardFilters.notBanned().test(this.pwn));
	}

	@Test
	public void testAcceptableProtocol() {
		assertFalse(StandardFilters.acceptableProtocol().test(this.pws));
		assertTrue(StandardFilters.acceptableProtocol().test(this.pwn));
	}

	@Test
	public void testOneOf() {
		ImmutableSet<EUID> tester = ImmutableSet.of(this.nidPwn);
		assertFalse(StandardFilters.oneOf(tester).test(this.pws));
		assertFalse(StandardFilters.oneOf(tester).test(this.pwt)); // No nid -> can't match
		assertTrue(StandardFilters.oneOf(tester).test(this.pwn));

		ImmutableList<EUID> tester2 = ImmutableList.copyOf(tester);
		assertFalse(StandardFilters.oneOf(tester2).test(this.pws));
		assertFalse(StandardFilters.oneOf(tester2).test(this.pwt));
		assertTrue(StandardFilters.oneOf(tester2).test(this.pwn));
	}

	@Test
	public void testRecentlyActive() {
		this.pwn.setTimestamp(Timestamps.ACTIVE, 0L);
		this.pwt.setTimestamp(Timestamps.ACTIVE, Time.currentTimestamp());
		assertTrue(StandardFilters.recentlyActive(1000).test(this.pwt));
		assertFalse(StandardFilters.recentlyActive(1000).test(this.pwn));
	}

}
