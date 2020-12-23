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

import org.junit.Test;

import com.radixdlt.properties.RuntimeProperties;

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
