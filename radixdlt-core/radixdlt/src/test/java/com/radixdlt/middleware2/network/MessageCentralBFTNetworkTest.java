/*
 *  (C) Copyright 2020 Radix DLT Ltd
 *
 *  Radix DLT Ltd licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except in
 *  compliance with the License.  You may obtain a copy of the
 *  License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 *  either express or implied.  See the License for the specific
 *  language governing permissions and limitations under the License.
 */

package com.radixdlt.middleware2.network;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.network.messaging.MessageCentral;
import com.radixdlt.consensus.Vote;
import com.radixdlt.network.messaging.MessageCentralMockProvider;

import com.radixdlt.network.p2p.NodeId;
import org.junit.Before;
import org.junit.Test;

public class MessageCentralBFTNetworkTest {
	private BFTNode self;
	private MessageCentral messageCentral;
	private MessageCentralBFTNetwork network;

	@Before
	public void setUp() {
		this.self = mock(BFTNode.class);
		this.messageCentral = MessageCentralMockProvider.get();
		this.network = new MessageCentralBFTNetwork(self, 0, messageCentral);
	}

	@Test
	public void when_send_vote__then_message_central_should_be_sent_vote_message() {
		Vote vote = mock(Vote.class);
		ECPublicKey leaderPk = ECKeyPair.generateNew().getPublicKey();
		BFTNode leader = mock(BFTNode.class);
		when(leader.getKey()).thenReturn(leaderPk);

		network.voteDispatcher().dispatch(leader, vote);
		verify(messageCentral, times(1)).send(eq(NodeId.fromPublicKey(leaderPk)), any(ConsensusEventMessage.class));
	}
}