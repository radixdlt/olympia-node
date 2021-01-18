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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.hash.HashCode;
import com.radixdlt.crypto.Hasher;
import com.radixdlt.consensus.VerifiedLedgerHeaderAndProof;
import com.radixdlt.consensus.bft.BFTValidatorSet;
import com.radixdlt.consensus.epoch.EpochChange;
import com.radixdlt.epochs.EpochChangeManager;
import com.radixdlt.epochs.EpochChangeManager.EpochsLedgerUpdateSender;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;

public class EpochChangeManagerTest {
	private EpochChangeManager epochChangeManager;
	private EpochsLedgerUpdateSender epochsLedgerUpdateSender;
	private Hasher hasher;

	@Before
	public void setup() {
		this.epochsLedgerUpdateSender = mock(EpochsLedgerUpdateSender.class);
		this.hasher = mock(Hasher.class);
		epochChangeManager = new EpochChangeManager(epochsLedgerUpdateSender, hasher);
	}

	@Test
	public void when_sending_committed_atom_with_epoch_change__then_should_send_epoch_change() {
		long genesisEpoch = 123;
		BFTValidatorSet validatorSet = mock(BFTValidatorSet.class);
		VerifiedLedgerHeaderAndProof tailHeader = mock(VerifiedLedgerHeaderAndProof.class);
		when(tailHeader.getEpoch()).thenReturn(genesisEpoch);
		when(tailHeader.isEndOfEpoch()).thenReturn(true);
		when(tailHeader.getStateVersion()).thenReturn(1234L);
		when(hasher.hash(any())).thenReturn(mock(HashCode.class));

		LedgerUpdate ledgerUpdate = mock(LedgerUpdate.class);
		when(ledgerUpdate.getTail()).thenReturn(tailHeader);
		when(ledgerUpdate.getNextValidatorSet()).thenReturn(Optional.of(validatorSet));
		epochChangeManager.sendLedgerUpdate(ledgerUpdate);

		verify(epochsLedgerUpdateSender, times(1))
			.sendLedgerUpdate(
				argThat(e -> {
					Optional<EpochChange> epochChange = e.getEpochChange();
					return epochChange.isPresent()
						&& epochChange.get().getProof().equals(tailHeader)
						&& epochChange.get().getBFTConfiguration().getValidatorSet().equals(validatorSet)
						&& epochChange.get().getEpoch() == 124L
						&& epochChange.get().getBFTConfiguration().getVertexStoreState().getRoot().getView().isGenesis();
				})
			);
	}
}