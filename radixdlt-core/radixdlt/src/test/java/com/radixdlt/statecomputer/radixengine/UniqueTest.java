/*
 * (C) Copyright 2021 Radix DLT Ltd
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
 *
 */

package com.radixdlt.statecomputer.radixengine;

import com.google.inject.Inject;
import com.google.inject.Injector;
import com.radixdlt.SingleNodeAndPeersDeterministicNetworkModule;
import com.radixdlt.atom.TxLowLevelBuilder;
import com.radixdlt.atom.Txn;
import com.radixdlt.atommodel.unique.UniqueParticle;
import com.radixdlt.atomos.RRIParticle;
import com.radixdlt.atomos.RriId;
import com.radixdlt.engine.RadixEngineException;
import com.radixdlt.mempool.MempoolConfig;
import com.radixdlt.statecomputer.EpochCeilingView;
import com.radixdlt.statecomputer.LedgerAndBFTProof;
import com.radixdlt.statecomputer.checkpoint.MockedGenesisModule;
import com.radixdlt.store.DatabaseLocation;
import org.junit.Rule;
import org.junit.Test;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.name.Names;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.engine.RadixEngine;
import com.radixdlt.identifiers.RRI;
import org.junit.rules.TemporaryFolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

public final class UniqueTest {
	@Rule
	public TemporaryFolder folder = new TemporaryFolder();

	@Inject private RadixEngine<LedgerAndBFTProof> sut;

	private Injector createInjector() {
		return Guice.createInjector(
			new SingleNodeAndPeersDeterministicNetworkModule(),
			new MockedGenesisModule(),
			new AbstractModule() {
				@Override
				protected void configure() {
					bindConstant().annotatedWith(Names.named("numPeers")).to(0);
					bind(MempoolConfig.class).toInstance(MempoolConfig.of(1000L, 10L));
					bindConstant().annotatedWith(DatabaseLocation.class).to(folder.getRoot().getAbsolutePath());
					bind(View.class).annotatedWith(EpochCeilingView.class).toInstance(View.of(100));
				}
			}
		);
	}

	private Txn uniqueTxn(ECKeyPair keyPair) {
		var rri = RRI.of(keyPair.getPublicKey(), "test");
		var rriParticle = new RRIParticle(rri);
		var rriId = RriId.fromRri(rri);
		var uniqueParticle = new UniqueParticle(rriId);
		var atomBuilder = TxLowLevelBuilder.newBuilder()
			.virtualDown(rriParticle)
			.up(uniqueParticle)
			.particleGroup();
		var sig = keyPair.sign(atomBuilder.hashToSign());
		return atomBuilder.sig(sig).build();
	}

	@Test
	public void conflicting_atoms_should_not_be_committed() throws RadixEngineException {
		// Arrange
		createInjector().injectMembers(this);
		var keyPair = ECKeyPair.generateNew();
		var committedAtom0 = uniqueTxn(keyPair);
		sut.execute(List.of(committedAtom0));

		// Act/Assert
		var committedAtom1 = uniqueTxn(keyPair);
		assertThatThrownBy(() -> sut.execute(List.of(committedAtom1))).isInstanceOf(RadixEngineException.class);
	}
}