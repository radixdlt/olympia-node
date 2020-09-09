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
import com.radixdlt.consensus.Hasher;
import com.radixdlt.consensus.LedgerHeader;
import com.radixdlt.consensus.QuorumCertificate;
import com.radixdlt.consensus.UnverifiedVertex;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.BFTValidatorSet;
import com.radixdlt.consensus.bft.VerifiedVertex;
import com.radixdlt.crypto.Hash;
import java.util.function.Function;

public class MockedBFTConfigurationOneDifferentGenesisModule extends AbstractModule {
	private static final Hash ONE_HASH = Hash.of(new byte[] {1});
	private final BFTNode nodeWithDifferentGenesis;

	public MockedBFTConfigurationOneDifferentGenesisModule(BFTNode nodeWithDifferentGenesis) {
		this.nodeWithDifferentGenesis = nodeWithDifferentGenesis;
	}

	@Provides
	@Singleton
	Function<BFTNode, BFTConfiguration> config(BFTValidatorSet validatorSet, Hasher hasher) {
		return node -> {
			Hash genesisHeaderHash = node.equals(nodeWithDifferentGenesis) ? Hash.ZERO_HASH : ONE_HASH;
			UnverifiedVertex genesis = UnverifiedVertex.createGenesis(LedgerHeader.genesis(genesisHeaderHash));
			Hash vertexHash = hasher.hash(genesis);
			VerifiedVertex genesisVertex = new VerifiedVertex(genesis, vertexHash);
			return new BFTConfiguration(
				validatorSet,
				genesisVertex,
				QuorumCertificate.ofGenesis(genesisVertex, LedgerHeader.genesis(genesisHeaderHash))
			);
		};
	}
}
