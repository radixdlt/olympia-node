package org.radix.network2.addressbook;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.radix.modules.Modules;
import org.radix.network.discovery.Whitelist;
import org.radix.network2.transport.StaticTransportMetadata;
import org.radix.network2.transport.TransportInfo;
import org.radix.network2.transport.udp.UDPConstants;
import org.radix.properties.RuntimeProperties;
import org.radix.serialization.TestSetupUtils;
import org.radix.shards.ShardSpace;
import org.radix.universe.system.LocalSystem;

import com.radixdlt.common.EUID;
import com.radixdlt.universe.Universe;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * Tests for parts of StandardFilters that can't be tested without
 * other parts of the system (singletons etc).
 */
public class StandardFilters2Test {

	@BeforeClass
	public static void beforeClass() {
		TestSetupUtils.installBouncyCastleProvider();
	}

	@Test
	@Ignore("Haven't bothered with this as all the components are checked")
	public void testStandardFilter() {
		fail("Not yet implemented");
	}

	@Test
	public void testIsWhitelisted() {
		final TransportInfo localhost1 = TransportInfo.of(
			UDPConstants.UDP_NAME,
			StaticTransportMetadata.of(
				UDPConstants.METADATA_UDP_HOST, "127.0.0.1",
				UDPConstants.METADATA_UDP_PORT, "10000"
			)
		);
		final TransportInfo localhost2 = TransportInfo.of(
			UDPConstants.UDP_NAME,
			StaticTransportMetadata.of(
				UDPConstants.METADATA_UDP_HOST, "127.0.0.2",
				UDPConstants.METADATA_UDP_PORT, "10000"
			)
		);
		final Peer peer1 = new PeerWithTransport(localhost1);
		final Peer peer2 = new PeerWithTransport(localhost2);
		final Peer peer3 = new PeerWithTransport(TransportInfo.of("DUMMY", StaticTransportMetadata.empty()));
		RuntimeProperties properties1 = mock(RuntimeProperties.class);
		when(properties1.get(eq("network.whitelist"), any())).thenReturn("");
		Whitelist whitelist = Whitelist.from(properties1);
		assertTrue(StandardFilters.isWhitelisted(whitelist).test(peer1));
		assertTrue(StandardFilters.isWhitelisted(whitelist).test(peer2));
		assertTrue(StandardFilters.isWhitelisted(whitelist).test(peer3)); // No host, whitelisted

		RuntimeProperties properties2 = mock(RuntimeProperties.class);
		when(properties2.get(eq("network.whitelist"), any())).thenReturn("127.0.0.1");
		whitelist = Whitelist.from(properties2);
		assertTrue(StandardFilters.isWhitelisted(whitelist).test(peer1));
		assertFalse(StandardFilters.isWhitelisted(whitelist).test(peer2));
		assertTrue(StandardFilters.isWhitelisted(whitelist).test(peer3));
	}

	@Test
	public void testNotOurNID() {
		RuntimeProperties properties = mock(RuntimeProperties.class);
		when(properties.get(eq("node.key.path"), any())).thenReturn("node.ks");
		when(properties.get(eq("shards.range"), anyLong())).thenReturn(ShardSpace.SHARD_CHUNK_RANGE);

		EUID self = EUID.ZERO;
		Peer ourNid = new PeerWithNid(self);
		Peer notOurNid = new PeerWithNid(EUID.ONE);
		Peer noNidAtAll = new PeerWithTransport(TransportInfo.of("DUMMY", StaticTransportMetadata.empty()));
		assertFalse(StandardFilters.notOurNID(self).test(ourNid));
		assertTrue(StandardFilters.notOurNID(self).test(notOurNid));
		assertTrue(StandardFilters.notOurNID(self).test(noNidAtAll));
	}
}
