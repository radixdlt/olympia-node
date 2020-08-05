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

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.radixdlt.consensus.ConsensusEventsRx;
import com.radixdlt.consensus.SyncEpochsRPCRx;
import com.radixdlt.consensus.SyncVerticesRPCRx;
import com.radixdlt.consensus.bft.BFTEventReducer.BFTEventSender;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.VertexStore.SyncVerticesRPCSender;
import com.radixdlt.consensus.epoch.EpochManager.SyncEpochsRPCSender;
import com.radixdlt.mempool.MempoolNetworkRx;
import com.radixdlt.mempool.MempoolNetworkTx;
import com.radixdlt.middleware2.network.MessageCentralBFTNetwork;
import com.radixdlt.middleware2.network.MessageCentralValidatorSync;
import com.radixdlt.middleware2.network.SimpleMempoolNetwork;
import com.radixdlt.network.addressbook.AddressBook;
import com.radixdlt.network.messaging.MessageCentral;
import com.radixdlt.universe.Universe;

public class NetworkModule extends AbstractModule {

	@Override
	protected void configure() {
		// provides (for SharedMempool)
		bind(SimpleMempoolNetwork.class).in(Scopes.SINGLETON);
		bind(MempoolNetworkRx.class).to(SimpleMempoolNetwork.class);
		bind(MempoolNetworkTx.class).to(SimpleMempoolNetwork.class);


		// Network BFT/Epoch Sync messages
		bind(SyncEpochsRPCSender.class).to(MessageCentralValidatorSync.class);
		bind(SyncEpochsRPCRx.class).to(MessageCentralValidatorSync.class);
		bind(SyncVerticesRPCSender.class).to(MessageCentralValidatorSync.class);
		bind(SyncVerticesRPCRx.class).to(MessageCentralValidatorSync.class);

		// Network BFT messages
		bind(BFTEventSender.class).to(MessageCentralBFTNetwork.class);
		bind(ConsensusEventsRx.class).to(MessageCentralBFTNetwork.class);
	}


	@Provides
	@Singleton
	MessageCentralValidatorSync validatorSync(
		@Named("self") BFTNode self,
		Universe universe,
		AddressBook addressBook,
		MessageCentral messageCentral
	) {
		return new MessageCentralValidatorSync(self, universe, addressBook, messageCentral);
	}

	@Provides
	@Singleton
	MessageCentralBFTNetwork bftNetwork(
		@Named("self") BFTNode self,
		Universe universe,
		AddressBook addressBook,
		MessageCentral messageCentral
	) {
		return new MessageCentralBFTNetwork(self, universe, addressBook, messageCentral);
	}
}
