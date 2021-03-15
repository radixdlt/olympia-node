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

package com.radixdlt.statecomputer;

import com.google.common.hash.HashCode;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.radixdlt.SingleNodeAndPeersDeterministicNetworkModule;
import com.radixdlt.atom.Atom;
import com.radixdlt.atommodel.unique.UniqueParticle;
import com.radixdlt.atomos.RRIParticle;
import com.radixdlt.constraintmachine.Spin;
import com.radixdlt.engine.RadixEngineException;
import com.radixdlt.identifiers.RadixAddress;
import com.radixdlt.mempool.MempoolMaxSize;
import com.radixdlt.mempool.MempoolThrottleMs;
import com.radixdlt.atom.ParticleGroup;
import com.radixdlt.atom.ClientAtom;
import com.radixdlt.statecomputer.checkpoint.MockedGenesisAtomModule;
import com.radixdlt.store.DatabaseLocation;
import org.junit.Rule;
import org.junit.Test;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.name.Names;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.Hasher;
import com.radixdlt.engine.RadixEngine;
import com.radixdlt.identifiers.RRI;
import com.radixdlt.atom.LedgerAtom;
import org.junit.rules.TemporaryFolder;

import java.util.Random;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

public final class RadixEngineTest {
	private Random random = new Random();
	@Rule
	public TemporaryFolder folder = new TemporaryFolder();

	@Inject private Hasher hasher;
	@Inject private RadixEngine<LedgerAtom> sut;

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

	private ClientAtom uniqueAtom(ECKeyPair keyPair) {
		RadixAddress address = new RadixAddress((byte) 0, keyPair.getPublicKey());
		RRI rri = RRI.of(address, "test");
		RRIParticle rriParticle = new RRIParticle(rri, 0);
		UniqueParticle uniqueParticle = new UniqueParticle("test", address, random.nextLong());
		ParticleGroup particleGroup = ParticleGroup.builder()
			.addParticle(rriParticle, Spin.DOWN)
			.addParticle(uniqueParticle, Spin.UP)
			.build();
		Atom atom = new Atom();
		atom.addParticleGroup(particleGroup);
		HashCode hashToSign = ClientAtom.computeHashToSign(atom);
		atom.setSignature(keyPair.euid(), keyPair.sign(hashToSign));
		return ClientAtom.convertFromApiAtom(atom);
	}

	@Test
	public void conflicting_atoms_should_not_be_committed() throws RadixEngineException {
		// Arrange
		createInjector().injectMembers(this);
		ECKeyPair keyPair = ECKeyPair.generateNew();
		CommittedAtom committedAtom0 = CommittedAtom.create(uniqueAtom(keyPair), 0);
		sut.checkAndStore(committedAtom0);

		// Act/Assert
		CommittedAtom committedAtom1 = CommittedAtom.create(uniqueAtom(keyPair), 1);
		assertThatThrownBy(() -> sut.checkAndStore(committedAtom1)).isInstanceOf(RadixEngineException.class);
	}
}