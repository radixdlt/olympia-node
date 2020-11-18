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

package com.radixdlt.integration.distributed;

import com.google.common.hash.HashCode;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.radixdlt.consensus.BFTConfiguration;
import com.radixdlt.consensus.LedgerHeader;
import com.radixdlt.consensus.QuorumCertificate;
import com.radixdlt.consensus.UnverifiedVertex;
import com.radixdlt.consensus.VerifiedLedgerHeaderAndProof;
import com.radixdlt.consensus.bft.BFTValidatorSet;
import com.radixdlt.consensus.bft.VerifiedVertex;
import com.radixdlt.crypto.HashUtils;
import com.radixdlt.store.LastProof;

/**
 * An initial bft configuration
 */
public class MockedBFTConfigurationModule extends AbstractModule {

	private final HashCode genesisHash;

	public MockedBFTConfigurationModule() {
		this(HashUtils.zero256());
	}

	public MockedBFTConfigurationModule(HashCode genesisHash) {
		this.genesisHash = genesisHash;
	}

	@Provides
	BFTConfiguration config(BFTValidatorSet validatorSet) {
		UnverifiedVertex genesis = UnverifiedVertex.createGenesis(LedgerHeader.genesis(genesisHash, validatorSet));
		VerifiedVertex hashedGenesis = new VerifiedVertex(genesis, genesisHash);

		return new BFTConfiguration(
			validatorSet,
			hashedGenesis,
			QuorumCertificate.ofGenesis(hashedGenesis, LedgerHeader.genesis(genesisHash, null))
		);
	}

	@Provides
	@LastProof
	VerifiedLedgerHeaderAndProof lastProof(BFTConfiguration configuration) {
		return configuration.getGenesisHeader();
	}
}
