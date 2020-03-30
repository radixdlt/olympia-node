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

package org.radix.network2.addressbook;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.radixdlt.identifiers.EUID;
import com.radixdlt.universe.Universe;
import org.junit.Before;
import org.junit.Test;
import org.radix.Radix;
import org.radix.network.Interfaces;
import org.radix.network2.transport.StaticTransportMetadata;
import org.radix.network2.transport.TransportInfo;
import org.radix.network2.transport.udp.UDPConstants;
import org.radix.time.Time;
import org.radix.time.Timestamps;
import org.radix.universe.system.RadixSystem;

import java.util.stream.Stream;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class StandardFiltersTest {

	private EUID nidPwn;
	private EUID nidPws;
	private RadixSystem system;
	private TransportInfo transportInfo;

	private PeerWithNid pwn;
	private PeerWithSystem pws;
	private PeerWithTransport pwt;
	private Interfaces interfaces;

	@Before
	public void setUp() throws Exception {
		this.nidPwn = EUID.ONE;
		this.nidPws = EUID.TWO;
		this.transportInfo = TransportInfo.of(UDPConstants.UDP_NAME,
			StaticTransportMetadata.of(
				UDPConstants.METADATA_UDP_HOST, "127.0.0.1",
				UDPConstants.METADATA_UDP_PORT, "10000"
			)
		);
		this.system = mock(RadixSystem.class);
		when(system.supportedTransports()).thenAnswer(invocation -> Stream.of(this.transportInfo));
		when(system.getNID()).thenReturn(this.nidPws);
		when(system.getAgentVersion()).thenReturn(Radix.MAJOR_AGENT_VERSION);

		this.pwn = new PeerWithNid(this.nidPwn);
		this.pws = new PeerWithSystem(this.system);
		this.pwt = new PeerWithTransport(this.transportInfo);

		interfaces = mock(Interfaces.class);
		when(interfaces.isSelf(any())).thenReturn(true);
		Universe universe = mock(Universe.class);
		when(universe.getPlanck()).thenReturn(86400L * 1000L); // 1 day
	}

	@Test
	public void testHasTransports() {
		assertTrue(StandardFilters.hasTransports().test(this.pwt));
		assertFalse(StandardFilters.hasTransports().test(this.pwn));
	}

	@Test
	public void testNotLocalAddress() {
		assertFalse(StandardFilters.notLocalAddress(interfaces).test(this.pwt));
		assertTrue(StandardFilters.notLocalAddress(interfaces).test(this.pwn));
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
