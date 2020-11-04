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

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Names;
import com.radixdlt.DefaultSerialization;
import com.radixdlt.consensus.BFTConfiguration;
import com.radixdlt.consensus.LedgerHeader;
import com.radixdlt.consensus.QuorumCertificate;
import com.radixdlt.consensus.UnverifiedVertex;
import com.radixdlt.consensus.VerifiedLedgerHeaderAndProof;
import com.radixdlt.consensus.bft.BFTValidatorSet;
import com.radixdlt.consensus.bft.VerifiedVertex;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.crypto.HashUtils;
import com.radixdlt.middleware2.LedgerAtom;
import com.radixdlt.serialization.Serialization;
import com.radixdlt.store.EngineStore;
import com.radixdlt.store.InMemoryEngineStore;
import com.radixdlt.store.LastEpochProof;
import com.radixdlt.store.LastProof;

public class MockedRadixEngineStoreModule extends AbstractModule {
	@Override
	public void configure() {
		bind(Serialization.class).toInstance(DefaultSerialization.getInstance());
		bind(Integer.class).annotatedWith(Names.named("magic")).toInstance(1);
		bind(new TypeLiteral<EngineStore<LedgerAtom>>() { }).to(new TypeLiteral<InMemoryEngineStore<LedgerAtom>>() { })
			.in(Scopes.SINGLETON);
	}

	@Provides
	private BFTConfiguration configuration(
		@LastEpochProof VerifiedLedgerHeaderAndProof proof,
		BFTValidatorSet validatorSet
	) {
		LedgerHeader nextLedgerHeader = LedgerHeader.create(
			proof.getEpoch() + 1,
			View.genesis(),
			proof.getAccumulatorState(),
			proof.timestamp()
		);
		UnverifiedVertex genesis = UnverifiedVertex.createGenesis(nextLedgerHeader);
		VerifiedVertex verifiedGenesis = new VerifiedVertex(genesis, HashUtils.zero256());
		QuorumCertificate genesisQC = QuorumCertificate.ofGenesis(verifiedGenesis, nextLedgerHeader);
		return new BFTConfiguration(validatorSet, verifiedGenesis, genesisQC);
	}

	@Provides
	@LastEpochProof
	public VerifiedLedgerHeaderAndProof lastEpochProof(BFTValidatorSet validatorSet) {
		return VerifiedLedgerHeaderAndProof.genesis(HashUtils.zero256(), validatorSet);
	}

	@Provides
	@LastProof
	public VerifiedLedgerHeaderAndProof lastProof(BFTValidatorSet validatorSet) {
		return VerifiedLedgerHeaderAndProof.genesis(HashUtils.zero256(), validatorSet);
	}
}
