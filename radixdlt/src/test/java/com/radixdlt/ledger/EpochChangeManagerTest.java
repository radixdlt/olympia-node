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

package com.radixdlt.ledger;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.radixdlt.consensus.Command;
import com.radixdlt.consensus.VertexMetadata;
import com.radixdlt.consensus.bft.BFTValidatorSet;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;

public class EpochChangeManagerTest {
	private EpochChangeManager epochChangeManager;
	private EpochChangeSender sender;

	@Before
	public void setup() {
		sender = mock(EpochChangeSender.class);
		epochChangeManager = new EpochChangeManager(sender);
	}

	@Test
	public void when_sending_committed_atom_with_epoch_change__then_should_send_epoch_change() {
		VertexMetadata vertexMetadata = mock(VertexMetadata.class);
		long genesisEpoch = 123;
		when(vertexMetadata.getEpoch()).thenReturn(genesisEpoch);
		when(vertexMetadata.isEndOfEpoch()).thenReturn(true);
		when(vertexMetadata.getStateVersion()).thenReturn(1234L);
		BFTValidatorSet validatorSet = mock(BFTValidatorSet.class);
		when(vertexMetadata.getValidatorSet()).thenReturn(Optional.of(validatorSet));

		epochChangeManager.sendCommitted(mock(Command.class), vertexMetadata);

		verify(sender, times(1))
			.epochChange(
				argThat(e -> e.getAncestor().equals(vertexMetadata) && e.getValidatorSet().equals(validatorSet))
			);
	}
}