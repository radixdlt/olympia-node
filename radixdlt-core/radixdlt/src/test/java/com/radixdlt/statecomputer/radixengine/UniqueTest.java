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

import com.radixdlt.statecomputer.forks.ForksModule;
import com.radixdlt.statecomputer.forks.RERulesConfig;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.radixdlt.SingleNodeAndPeersDeterministicNetworkModule;
import com.radixdlt.atom.TxLowLevelBuilder;
import com.radixdlt.atom.Txn;
import com.radixdlt.atomos.UnclaimedREAddr;
import com.radixdlt.constraintmachine.SubstateSerialization;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.engine.RadixEngine;
import com.radixdlt.engine.RadixEngineException;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.mempool.MempoolConfig;
import com.radixdlt.qualifier.NumPeers;
import com.radixdlt.statecomputer.LedgerAndBFTProof;
import com.radixdlt.statecomputer.RadixEngineConfig;
import com.radixdlt.statecomputer.checkpoint.MockedGenesisModule;
import com.radixdlt.statecomputer.forks.RadixEngineForksLatestOnlyModule;
import com.radixdlt.store.DatabaseLocation;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

public final class UniqueTest {
	@Rule
	public TemporaryFolder folder = new TemporaryFolder();

	@Inject
	private RadixEngine<LedgerAndBFTProof> sut;

	@Inject
	private SubstateSerialization substateSerialization;

	private Injector createInjector() {
		return Guice.createInjector(
			MempoolConfig.asModule(1000, 10),
			new RadixEngineForksLatestOnlyModule(new RERulesConfig(false, 100)),
			new ForksModule(),
			RadixEngineConfig.asModule(1, 100, 50),
			new SingleNodeAndPeersDeterministicNetworkModule(),
			new MockedGenesisModule(),
			new AbstractModule() {
				@Override
				protected void configure() {
					bindConstant().annotatedWith(NumPeers.class).to(0);
					bindConstant().annotatedWith(DatabaseLocation.class).to(folder.getRoot().getAbsolutePath());
				}
			}
		);
	}

	private Txn uniqueTxn(ECKeyPair keyPair) {
		var addr = REAddr.ofHashedKey(keyPair.getPublicKey(), "test");
		var rriParticle = new UnclaimedREAddr(addr);
		var atomBuilder = TxLowLevelBuilder.newBuilder(substateSerialization)
			.virtualDown(rriParticle, "test".getBytes(StandardCharsets.UTF_8))
			.end();
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