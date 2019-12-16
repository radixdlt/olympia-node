package org.radix.network2.addressbook;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.radix.Radix;
import org.radix.modules.Modules;
import org.radix.network.discovery.Whitelist;
import org.radix.network2.transport.StaticTransportMetadata;
import org.radix.network2.transport.TransportInfo;
import org.radix.network2.transport.udp.UDPConstants;
import org.radix.properties.RuntimeProperties;
import org.radix.serialization.TestSetupUtils;
import org.radix.shards.ShardSpace;
import org.radix.universe.system.LocalSystem;
import org.radix.universe.system.RadixSystem;

import com.google.common.collect.ImmutableList;
import com.radixdlt.common.EUID;
import com.radixdlt.crypto.CryptoException;
import com.radixdlt.crypto.ECKeyPair;
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

	@Before
	public void setUp() throws Exception {
		Universe universe = mock(Universe.class);
		Modules.put(Universe.class, universe);
	}

	@After
	public void tearDown() throws Exception {
		Modules.remove(Universe.class);
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
		try {
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
		} finally {
			Modules.remove(RuntimeProperties.class);
		}
	}

	@Test
	public void testNotOurNID() {
		try {
			RuntimeProperties properties = mock(RuntimeProperties.class);
			when(properties.get(eq("node.key.path"), any())).thenReturn("node.ks");
			when(properties.get(eq("shards.range"), anyLong())).thenReturn(ShardSpace.SHARD_CHUNK_RANGE);
			Modules.put(RuntimeProperties.class, properties);

			LocalSystem.reset();
			Peer ourNid = new PeerWithNid(LocalSystem.getInstance().getNID());
			Peer notOurNid = new PeerWithNid(EUID.ONE);
			Peer noNidAtAll = new PeerWithTransport(TransportInfo.of("DUMMY", StaticTransportMetadata.empty()));
			assertFalse(StandardFilters.notOurNID().test(ourNid));
			assertTrue(StandardFilters.notOurNID().test(notOurNid));
			assertTrue(StandardFilters.notOurNID().test(noNidAtAll));
		} finally {
			LocalSystem.reset();
			Modules.remove(RuntimeProperties.class);
		}
	}

	@Test
	public void testHasOverlappingShards() throws CryptoException {
		try {
			RuntimeProperties properties = mock(RuntimeProperties.class);
			when(properties.get(eq("node.key.path"), any())).thenReturn("node.ks");
			when(properties.get(eq("shards.range"), anyLong())).thenReturn(1L);
			Modules.put(RuntimeProperties.class, properties);

			LocalSystem.reset();
			ShardSpace ourSp = LocalSystem.getInstance().getShards();
			RadixSystem system;
			do {
				ECKeyPair key = new ECKeyPair();
				ShardSpace sp = new ShardSpace(key.getUID().getShard(), 1L);
				system = new RadixSystem(key.getPublicKey(), Radix.AGENT, Radix.AGENT_VERSION, Radix.PROTOCOL_VERSION, sp, ImmutableList.of());
				// Chances of this actually looping are pretty low
			} while (ourSp.intersects(system.getShards()));

			Peer our = new PeerWithSystem(LocalSystem.getInstance());
			Peer notOur = new PeerWithSystem(system);
			Peer noShards = new PeerWithNid(EUID.ONE);
			assertTrue(StandardFilters.hasOverlappingShards().test(our));
			assertFalse(StandardFilters.hasOverlappingShards().test(notOur));
			assertFalse(StandardFilters.hasOverlappingShards().test(noShards));
		} finally {
			LocalSystem.reset();
			Modules.remove(RuntimeProperties.class);
		}
	}
}
