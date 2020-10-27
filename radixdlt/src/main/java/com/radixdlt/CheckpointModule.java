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

package com.radixdlt;

import com.google.common.collect.ImmutableList;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.radixdlt.consensus.AddressBookGenesisValidatorSetProvider;
import com.radixdlt.consensus.Command;
import com.radixdlt.consensus.VerifiedLedgerHeaderAndProof;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.crypto.Hasher;
import com.radixdlt.ledger.VerifiedCommandsAndProof;
import com.radixdlt.middleware2.ClientAtom;
import com.radixdlt.network.addressbook.AddressBook;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.Serialization;
import com.radixdlt.universe.Universe;

public class CheckpointModule extends AbstractModule {
	private final int fixedNodeCount;

	public CheckpointModule(int fixedNodeCount) {
		this.fixedNodeCount = fixedNodeCount;
	}

	@Provides
	@Singleton
	private AddressBookGenesisValidatorSetProvider provider(
		AddressBook addressBook,
		@Named("self") BFTNode self
	) {
		return new AddressBookGenesisValidatorSetProvider(
			self.getKey(),
			addressBook,
			fixedNodeCount
		);
	}

	@Provides
	@Singleton
	private VerifiedCommandsAndProof genesisCheckpoint(
		Serialization serialization,
		Universe universe,
		AddressBookGenesisValidatorSetProvider initialValidatorSetProvider,
		Hasher hasher
	) {
		final ClientAtom genesisAtom = ClientAtom.convertFromApiAtom(universe.getGenesis().get(0), hasher);
		byte[] payload = serialization.toDson(genesisAtom, Output.ALL);
		Command command = new Command(payload);
		VerifiedLedgerHeaderAndProof genesisLedgerHeader = VerifiedLedgerHeaderAndProof.genesis(
			hasher.hash(command),
			initialValidatorSetProvider.getGenesisValidatorSet()
		);

		return new VerifiedCommandsAndProof(
			ImmutableList.of(command),
			genesisLedgerHeader
		);
	}
}
