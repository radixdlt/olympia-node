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
import com.radixdlt.atommodel.unique.UniqueParticle;
import com.radixdlt.atomos.RRIParticle;
import com.radixdlt.engine.RadixEngineException;
import com.radixdlt.identifiers.RadixAddress;
import com.radixdlt.mempool.MempoolMaxSize;
import com.radixdlt.mempool.MempoolThrottleMs;
import com.radixdlt.atom.Atom;
import com.radixdlt.statecomputer.EpochCeilingView;
import com.radixdlt.statecomputer.LedgerAndBFTProof;
import com.radixdlt.statecomputer.checkpoint.MockedGenesisAtomModule;
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
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

public final class UniqueTest {
	private Random random = new Random();
	@Rule
	public TemporaryFolder folder = new TemporaryFolder();

	@Inject private RadixEngine<LedgerAndBFTProof> sut;

	private Injector createInjector() {
		return Guice.createInjector(
			new SingleNodeAndPeersDeterministicNetworkModule(),
			new MockedGenesisAtomModule(),
			new AbstractModule() {
				@Override
				protected void configure() {
					bindConstant().annotatedWith(Names.named("numPeers")).to(0);
					bindConstant().annotatedWith(MempoolThrottleMs.class).to(10L);
					bindConstant().annotatedWith(MempoolMaxSize.class).to(1000);
					bindConstant().annotatedWith(DatabaseLocation.class).to(folder.getRoot().getAbsolutePath());
					bind(View.class).annotatedWith(EpochCeilingView.class).toInstance(View.of(100));
				}
			}
		);
	}

	private Atom uniqueAtom(ECKeyPair keyPair) {
		var address = new RadixAddress((byte) 0, keyPair.getPublicKey());
		var rri = RRI.of(address, "test");
		var rriParticle = new RRIParticle(rri, 0);
		var uniqueParticle = new UniqueParticle("test", address, random.nextLong());
		var atomBuilder = TxLowLevelBuilder.newBuilder()
			.virtualDown(rriParticle)
			.up(uniqueParticle)
			.particleGroup();
		return atomBuilder.signAndBuild(keyPair::sign);
	}

	@Test
	public void conflicting_atoms_should_not_be_committed() throws RadixEngineException {
		// Arrange
		createInjector().injectMembers(this);
		var keyPair = ECKeyPair.generateNew();
		var committedAtom0 = uniqueAtom(keyPair);
		sut.execute(List.of(committedAtom0));

		// Act/Assert
		var committedAtom1 = uniqueAtom(keyPair);
		assertThatThrownBy(() -> sut.execute(List.of(committedAtom1))).isInstanceOf(RadixEngineException.class);
	}
}