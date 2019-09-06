package org.radix.network2.addressbook;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.radix.properties.RuntimeProperties;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class PeerManagerConfigurationTest {

	@Before
	public void setUp() throws Exception {
	}

	@After
	public void tearDown() throws Exception {
	}

	@Test
	public void testFromRuntimeProperties() {
		RuntimeProperties properties = mock(RuntimeProperties.class);

		when(properties.get(eq("network.peers.broadcast.interval"), anyInt())).thenReturn(100);
		when(properties.get(eq("network.peers.probe.interval"), anyInt())).thenReturn(101);
		when(properties.get(eq("network.peers.probe.delay"), anyInt())).thenReturn(102);

		PeerManagerConfiguration config = PeerManagerConfiguration.fromRuntimeProperties(properties);

		assertEquals(100, config.networkPeersBroadcastInterval(-1));
		assertEquals(101, config.networkPeersProbeInterval(-1));
		assertEquals(102, config.networkPeersProbeDelay(-1));
	}
}
