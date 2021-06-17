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

package com.radixdlt.network.messaging;

import java.util.ArrayList;

import com.radixdlt.network.p2p.NodeId;
import org.junit.Test;
import org.radix.network.messages.PeerPingMessage;
import org.radix.network.messages.PeerPongMessage;
import org.radix.network.messaging.Message;

import com.google.common.collect.Lists;
import nl.jqno.equalsverifier.EqualsVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class OutboundMessageEventTest {

	@Test
	public void equalsContract() {
		EqualsVerifier.forClass(OutboundMessageEvent.class)
			.withRedefinedSuperclass()
			.verify();
	}

	@Test
	public void sensibleToString() {
		NodeId peer = mock(NodeId.class);
		Message message = mock(Message.class);
		long nanoTimeDiff = 123456789L;
		OutboundMessageEvent event = new OutboundMessageEvent(peer, message, nanoTimeDiff);

		String s = event.toString();
		assertThat(s)
			.contains(OutboundMessageEvent.class.getSimpleName())
			.contains(peer.toString())
			.contains(message.toString())
			.contains(String.valueOf(nanoTimeDiff))
			.contains("priority=0");
	}

	@Test
	public void peerPingToString() {
		NodeId peer = mock(NodeId.class);
		Message message = mock(PeerPingMessage.class);
		long nanoTimeDiff = 123456789L;
		OutboundMessageEvent event = new OutboundMessageEvent(peer, message, nanoTimeDiff);

		String s = event.toString();
		assertThat(s)
			.contains(OutboundMessageEvent.class.getSimpleName())
			.contains(peer.toString())
			.contains(message.toString())
			.contains(String.valueOf(nanoTimeDiff))
			.contains("priority=" + Integer.MIN_VALUE);
	}

	@Test
	public void peerPongToString() {
		NodeId peer = mock(NodeId.class);
		Message message = mock(PeerPongMessage.class);
		long nanoTimeDiff = 123456789L;
		OutboundMessageEvent event = new OutboundMessageEvent(peer, message, nanoTimeDiff);

		String s = event.toString();
		assertThat(s)
			.contains(OutboundMessageEvent.class.getSimpleName())
			.contains(peer.toString())
			.contains(message.toString())
			.contains(String.valueOf(nanoTimeDiff))
			.contains("priority=" + Integer.MIN_VALUE);
	}

	@Test
	public void comparatorCheck() {
		ArrayList<OutboundMessageEvent> events = Lists.newArrayList();
		events.add(makeMessageEventFor(Message.class));
		events.add(makeMessageEventFor(Message.class));
		events.add(makeMessageEventFor(Message.class));
		events.add(makeMessageEventFor(PeerPingMessage.class));
		assertFalse(events.get(0).message() instanceof PeerPingMessage);
		assertFalse(events.get(0).message() instanceof PeerPongMessage);
		events.sort(OutboundMessageEvent.comparator());
		assertTrue(events.get(0).message() instanceof PeerPingMessage);

		events.clear();
		events.add(makeMessageEventFor(Message.class));
		events.add(makeMessageEventFor(Message.class));
		events.add(makeMessageEventFor(Message.class));
		events.add(makeMessageEventFor(PeerPongMessage.class));
		assertFalse(events.get(0).message() instanceof PeerPingMessage);
		assertFalse(events.get(0).message() instanceof PeerPongMessage);
		events.sort(OutboundMessageEvent.comparator());
		assertTrue(events.get(0).message() instanceof PeerPongMessage);
	}

	private OutboundMessageEvent makeMessageEventFor(Class<? extends Message> cls) {
		NodeId peer = mock(NodeId.class);
		Message message = mock(cls);
		long nanoTimeDiff = 123456789L;
		return new OutboundMessageEvent(peer, message, nanoTimeDiff);
	}
}
