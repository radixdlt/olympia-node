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

import com.radixdlt.identifiers.EUID;
import com.radixdlt.network.transport.StaticTransportMetadata;
import com.radixdlt.network.transport.TransportInfo;
import com.radixdlt.network.transport.udp.UDPConstants;
import com.radixdlt.properties.RuntimeProperties;

import org.junit.BeforeClass;
import org.junit.Test;
import org.radix.network.discovery.Whitelist;
import org.radix.serialization.TestSetupUtils;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
	public void testIsWhitelisted() {
		final TransportInfo localhost1 = TransportInfo.of(
			UDPConstants.NAME,
			StaticTransportMetadata.of(
				UDPConstants.METADATA_HOST, "127.0.0.1",
				UDPConstants.METADATA_PORT, "10000"
			)
		);
		final TransportInfo localhost2 = TransportInfo.of(
			UDPConstants.NAME,
			StaticTransportMetadata.of(
				UDPConstants.METADATA_HOST, "127.0.0.2",
				UDPConstants.METADATA_PORT, "10000"
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


		EUID self = EUID.ZERO;
		Peer ourNid = new PeerWithNid(self);
		Peer notOurNid = new PeerWithNid(EUID.ONE);
		Peer noNidAtAll = new PeerWithTransport(TransportInfo.of("DUMMY", StaticTransportMetadata.empty()));
		assertFalse(StandardFilters.notOurNID(self).test(ourNid));
		assertTrue(StandardFilters.notOurNID(self).test(notOurNid));
		assertTrue(StandardFilters.notOurNID(self).test(noNidAtAll));
	}
}
