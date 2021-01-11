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

package org.radix.serialization;

import org.junit.Test;
import org.radix.Radix;
import org.radix.network.messages.PeersMessage;
import org.radix.universe.system.RadixSystem;

import com.google.common.collect.ImmutableList;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.network.addressbook.PeerWithSystem;
import com.radixdlt.network.transport.StaticTransportMetadata;
import com.radixdlt.network.transport.TransportInfo;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Check serialization of PeersMessage
 */
public class PeersMessageSerializeTest extends SerializeMessageObject<PeersMessage> {
	public PeersMessageSerializeTest() {
		super(PeersMessage.class, PeersMessageSerializeTest::get);
	}

	private static PeersMessage get() {
		PeerWithSystem p1 = new PeerWithSystem(new RadixSystem());
		PeerWithSystem p2 = new PeerWithSystem(new RadixSystem());
		PeerWithSystem p3 = new PeerWithSystem(new RadixSystem());
		PeersMessage pm = new PeersMessage(1, ImmutableList.of(p1, p2, p3));
		return pm;
	}

	@Test
	public void sensibleToString() {
		ECKeyPair key = ECKeyPair.generateNew();
		TransportInfo ti = TransportInfo.of("DUMMY", StaticTransportMetadata.empty());
		RadixSystem system = new RadixSystem(
				key.getPublicKey(), Radix.AGENT, Radix.AGENT_VERSION, Radix.PROTOCOL_VERSION, ImmutableList.of(ti));
		PeerWithSystem p = new PeerWithSystem(system);
		PeersMessage pm = new PeersMessage(1, ImmutableList.of(p));
		String s = pm.toString();

		assertThat(s)
			.contains(PeersMessage.class.getSimpleName())
			.contains(key.getPublicKey().euid().toString())
			.contains("DUMMY");
	}
}
