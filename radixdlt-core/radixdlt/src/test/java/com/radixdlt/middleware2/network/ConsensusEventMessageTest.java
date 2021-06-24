/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 */

package com.radixdlt.middleware2.network;

import com.google.common.hash.HashCode;
import com.radixdlt.crypto.HashUtils;
import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;
import org.junit.Test;

import com.radixdlt.consensus.Proposal;
import com.radixdlt.consensus.Vote;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.assertThat;

public class ConsensusEventMessageTest {

	@Test
	public void sensibleToStringProposal() {
		Proposal m = mock(Proposal.class);
		ConsensusEventMessage msg1 = new ConsensusEventMessage(m);
		String s1 = msg1.toString();

		assertThat(s1)
			.contains(ConsensusEventMessage.class.getSimpleName())
			.contains(m.toString());

		assertTrue(msg1.getConsensusMessage() instanceof Proposal);
	}

	@Test
	public void sensibleToStringVote() {
		Vote m = mock(Vote.class);
		ConsensusEventMessage msg1 = new ConsensusEventMessage(m);
		String s1 = msg1.toString();
		assertThat(s1)
			.contains(ConsensusEventMessage.class.getSimpleName())
			.contains(m.toString());

		assertTrue(msg1.getConsensusMessage() instanceof Vote);
	}

	@Test
	public void sensibleToStringNone() {
		ConsensusEventMessage msg1 = new ConsensusEventMessage();
		String s1 = msg1.toString();
		assertThat(s1)
			.contains(ConsensusEventMessage.class.getSimpleName())
			.contains("null");
	}

	@Test(expected = IllegalStateException.class)
	public void failedConsensusMessage() {
		ConsensusEventMessage msg1 = new ConsensusEventMessage();
		assertNotNull(msg1.getConsensusMessage());
	}

	@Test
	public void equalsContract() {
		EqualsVerifier.forClass(ConsensusEventMessage.class)
				.withIgnoredFields("instance")
				.suppress(Warning.NONFINAL_FIELDS)
				.withPrefabValues(HashCode.class, HashUtils.random256(), HashUtils.random256())
				.verify();
	}
}