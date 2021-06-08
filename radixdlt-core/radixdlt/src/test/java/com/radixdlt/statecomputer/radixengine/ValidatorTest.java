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

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.name.Names;
import com.radixdlt.SingleNodeAndPeersDeterministicNetworkModule;
import com.radixdlt.atom.TxLowLevelBuilder;
import com.radixdlt.atommodel.validators.state.ValidatorParticle;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.engine.RadixEngine;
import com.radixdlt.engine.RadixEngineException;
import com.radixdlt.mempool.MempoolConfig;
import com.radixdlt.statecomputer.LedgerAndBFTProof;
import com.radixdlt.statecomputer.RadixEngineConfig;
import com.radixdlt.statecomputer.checkpoint.MockedGenesisModule;
import com.radixdlt.statecomputer.forks.BetanetForksModule;
import com.radixdlt.statecomputer.forks.RadixEngineForksLatestOnlyModule;
import com.radixdlt.store.DatabaseLocation;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class ValidatorTest {
	@Rule
	public TemporaryFolder folder = new TemporaryFolder();

	@Inject
	private RadixEngine<LedgerAndBFTProof> sut;

	private Injector createInjector() {
		return Guice.createInjector(
			MempoolConfig.asModule(1000, 10),
			new BetanetForksModule(),
			new RadixEngineForksLatestOnlyModule(View.of(100)),
			RadixEngineConfig.asModule(1, 100, 50),
			new SingleNodeAndPeersDeterministicNetworkModule(),
			new MockedGenesisModule(),
			new AbstractModule() {
				@Override
				protected void configure() {
					bindConstant().annotatedWith(Names.named("numPeers")).to(0);
					bindConstant().annotatedWith(DatabaseLocation.class).to(folder.getRoot().getAbsolutePath());
				}
			}
		);
	}

	@Test
	public void cannot_register_twice() {
		// Arrange
		createInjector().injectMembers(this);
		var keyPair = ECKeyPair.generateNew();
		var builder = TxLowLevelBuilder.newBuilder()
			.virtualDown(new ValidatorParticle(keyPair.getPublicKey(), false))
			.up(new ValidatorParticle(keyPair.getPublicKey(), true))
			.up(new ValidatorParticle(keyPair.getPublicKey(), true));
		var sig = keyPair.sign(builder.hashToSign());
		var txn = builder.sig(sig).build();

		// Act/Assert
		assertThatThrownBy(() -> sut.execute(List.of(txn))).isInstanceOf(RadixEngineException.class);
	}
}
