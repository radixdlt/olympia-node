package org.radix.network2.addressbook;

import org.junit.Test;
import org.radix.properties.RuntimeProperties;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class PeerManagerConfigurationTest {

	@Test
	public void testFromRuntimeProperties() {
		RuntimeProperties properties = mock(RuntimeProperties.class);

		when(properties.get(eq("network.peers.broadcast.interval"), anyInt())).thenReturn(100);
		when(properties.get(eq("network.peers.broadcast.delay"), anyInt())).thenReturn(101);
		when(properties.get(eq("network.peers.probe.interval"), anyInt())).thenReturn(102);
		when(properties.get(eq("network.peers.probe.delay"), anyInt())).thenReturn(103);
		when(properties.get(eq("network.peers.probe.timeout"), anyInt())).thenReturn(104);
		when(properties.get(eq("network.peers.probe.frequency"), anyInt())).thenReturn(105);
		when(properties.get(eq("network.peers.heartbeat.interval"), anyInt())).thenReturn(106);
		when(properties.get(eq("network.peers.heartbeat.delay"), anyInt())).thenReturn(107);
		when(properties.get(eq("network.peers.discover.interval"), anyInt())).thenReturn(108);
		when(properties.get(eq("network.peers.discover.delay"), anyInt())).thenReturn(109);
		when(properties.get(eq("network.peers.message.batch.size"), anyInt())).thenReturn(110);


		PeerManagerConfiguration config = PeerManagerConfiguration.fromRuntimeProperties(properties);

		assertEquals(100, config.networkPeersBroadcastInterval(-1));
		assertEquals(101, config.networkPeersBroadcastDelay(-1));
		assertEquals(102, config.networkPeersProbeInterval(-1));
		assertEquals(103, config.networkPeersProbeDelay(-1));
		assertEquals(104, config.networkPeersProbeTimeout(-1));
		assertEquals(105, config.networkPeersProbeFrequency(-1));
		assertEquals(106, config.networkHeartbeatPeersInterval(-1));
		assertEquals(107, config.networkHeartbeatPeersDelay(-1));
		assertEquals(108, config.networkDiscoverPeersInterval(-1));
		assertEquals(109, config.networkDiscoverPeersDelay(-1));
		assertEquals(110, config.networkPeersMessageBatchSize(-1));
	}
}
