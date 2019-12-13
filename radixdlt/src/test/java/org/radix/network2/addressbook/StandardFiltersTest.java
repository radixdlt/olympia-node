package org.radix.network2.addressbook;

import java.util.stream.Stream;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.radix.Radix;
import org.radix.modules.Modules;
import org.radix.network.Interfaces;
import org.radix.network2.transport.StaticTransportMetadata;
import org.radix.network2.transport.TransportInfo;
import org.radix.network2.transport.udp.UDPConstants;
import org.radix.time.Time;
import org.radix.time.Timestamps;
import org.radix.universe.system.RadixSystem;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.radixdlt.common.EUID;
import com.radixdlt.universe.Universe;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

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
		Modules.put(Universe.class, universe);
	}

	@After
	public void tearDown() throws Exception {
		Modules.remove(Universe.class);
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
		assertTrue(StandardFilters.recentlyActive().test(this.pwt));
		assertFalse(StandardFilters.recentlyActive().test(this.pwn));
	}

}
