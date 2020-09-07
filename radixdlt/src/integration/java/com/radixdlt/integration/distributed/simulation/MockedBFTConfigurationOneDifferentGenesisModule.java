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

package com.radixdlt.integration.distributed.simulation;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.radixdlt.consensus.BFTConfiguration;
import com.radixdlt.consensus.LedgerHeader;
import com.radixdlt.consensus.QuorumCertificate;
import com.radixdlt.consensus.Vertex;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.crypto.Hash;
import java.util.function.Function;

public class MockedBFTConfigurationOneDifferentGenesisModule extends AbstractModule {
	private final static Hash ONE_HASH = Hash.of(new byte[] {1});
	private final BFTNode nodeWithDifferentGenesis;

	public MockedBFTConfigurationOneDifferentGenesisModule(BFTNode nodeWithDifferentGenesis) {
		this.nodeWithDifferentGenesis = nodeWithDifferentGenesis;
	}

	@Provides
	@Singleton
	Function<BFTNode, BFTConfiguration> config() {
		return node -> {
			Hash genesisHash = node.equals(nodeWithDifferentGenesis) ? Hash.ZERO_HASH : ONE_HASH;
			Vertex genesis = Vertex.createGenesis(LedgerHeader.genesis(genesisHash));
			return new BFTConfiguration(
				genesis,
				QuorumCertificate.ofGenesis(genesis, LedgerHeader.genesis(genesisHash))
			);
		};
	}
}
