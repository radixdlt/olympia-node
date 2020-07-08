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

package com.radixdlt.middleware2.network;

import com.google.inject.AbstractModule;
import com.google.inject.Scopes;
import com.radixdlt.consensus.ConsensusEventsRx;
import com.radixdlt.consensus.BFTEventSender;
import com.radixdlt.mempool.MempoolNetworkRx;
import com.radixdlt.mempool.MempoolNetworkTx;

public class NetworkModule extends AbstractModule {

	@Override
	protected void configure() {
		// provides (for SharedMempool)
		bind(SimpleMempoolNetwork.class).in(Scopes.SINGLETON);
		bind(MempoolNetworkRx.class).to(SimpleMempoolNetwork.class);
		bind(MempoolNetworkTx.class).to(SimpleMempoolNetwork.class);

		// Provides (for Event Coordinator)
		bind(MessageCentralBFTNetwork.class).in(Scopes.SINGLETON);
		bind(ConsensusEventsRx.class).to(MessageCentralBFTNetwork.class);
		bind(BFTEventSender.class).to(MessageCentralBFTNetwork.class);
	}
}
