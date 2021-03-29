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

import com.google.common.collect.ImmutableMap;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.name.Names;
import com.radixdlt.SingleNodeAndPeersDeterministicNetworkModule;
import com.radixdlt.atom.TxLowLevelBuilder;
import com.radixdlt.atom.SubstateId;
import com.radixdlt.atommodel.tokens.FixedSupplyTokenDefinitionParticle;
import com.radixdlt.atommodel.tokens.TransferrableTokensParticle;
import com.radixdlt.atomos.RRIParticle;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.engine.RadixEngine;
import com.radixdlt.engine.RadixEngineException;
import com.radixdlt.identifiers.RRI;
import com.radixdlt.identifiers.RadixAddress;
import com.radixdlt.mempool.MempoolMaxSize;
import com.radixdlt.mempool.MempoolThrottleMs;
import com.radixdlt.statecomputer.EpochCeilingView;
import com.radixdlt.statecomputer.LedgerAndBFTProof;
import com.radixdlt.statecomputer.checkpoint.MockedGenesisAtomModule;
import com.radixdlt.store.DatabaseLocation;
import com.radixdlt.utils.UInt256;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.util.List;

import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

public class FixedTokenTest {
	@Rule
	public TemporaryFolder folder = new TemporaryFolder();

	@Inject
	private RadixEngine<LedgerAndBFTProof> sut;

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

	private TransferrableTokensParticle createToken(ECKeyPair keyPair, TxLowLevelBuilder atomBuilder) {
		RadixAddress address = new RadixAddress((byte) 0, keyPair.getPublicKey());
		RRI rri = RRI.of(address, "XRD");
		var rriParticle = new RRIParticle(rri, 0);
		var fixedSupply = new FixedSupplyTokenDefinitionParticle(
			rri,
			"XRD",
			"Some description",
			UInt256.TEN,
			UInt256.ONE,
			null,
			null
		);
		var token = new TransferrableTokensParticle(
			address,
			UInt256.TEN,
			UInt256.ONE,
			rri,
			ImmutableMap.of(),
			System.currentTimeMillis()
		);

		atomBuilder
			.virtualDown(rriParticle)
			.up(fixedSupply)
			.up(token)
			.particleGroup();

		return token;
	}

	private void spendToken(TxLowLevelBuilder atomBuilder, TransferrableTokensParticle p, int times) {
		var token = new TransferrableTokensParticle(
			p.getAddress(),
			p.getAmount().multiply(UInt256.from(times)),
			p.getGranularity(),
			p.getTokDefRef(),
			ImmutableMap.of(),
			1
		);
		atomBuilder.up(token);

		for (int i = 0; i < times; i++) {
			atomBuilder.down(SubstateId.of(p));
		}
		atomBuilder.particleGroup();
	}

	@Test
	public void token_creation_then_spend_should_succeed() throws RadixEngineException {
		// Arrange
		createInjector().injectMembers(this);
		var keyPair = ECKeyPair.generateNew();
		var builder = TxLowLevelBuilder.newBuilder();
		var upToken = createToken(keyPair, builder);
		var atom = builder.signAndBuild(keyPair::sign);
		sut.execute(List.of(atom));
		var builder2 = TxLowLevelBuilder.newBuilder();
		spendToken(builder2, upToken, 1);
		var atom2 = builder2.signAndBuild(keyPair::sign);

		// Act/Assert
		sut.execute(List.of(atom2));
	}

	@Test
	public void token_creation_then_double_spend_should_fail() throws RadixEngineException {
		// Arrange
		createInjector().injectMembers(this);
		ECKeyPair keyPair = ECKeyPair.generateNew();
		var builder = TxLowLevelBuilder.newBuilder();
		var upToken = createToken(keyPair, builder);
		var atom = builder.signAndBuild(keyPair::sign);
		sut.execute(List.of(atom));


		var builder2 = TxLowLevelBuilder.newBuilder();
		spendToken(builder2, upToken, 2);
		var atom2 = builder2.signAndBuild(keyPair::sign);

		// Act/Assert
		assertThatThrownBy(() -> sut.execute(List.of(atom2))).isInstanceOf(RadixEngineException.class);
	}


	@Test
	public void atomic_token_creation_and_spend_should_succeed() throws RadixEngineException {
		// Arrange
		createInjector().injectMembers(this);
		ECKeyPair keyPair = ECKeyPair.generateNew();
		var builder = TxLowLevelBuilder.newBuilder();
		var upToken = createToken(keyPair, builder);
		spendToken(builder, upToken, 1);
		var atom = builder.signAndBuild(keyPair::sign);

		// Act/Assert
		sut.execute(List.of(atom));
	}


	@Test
	public void atomic_token_creation_and_double_spend_should_fail() {
		// Arrange
		createInjector().injectMembers(this);
		ECKeyPair keyPair = ECKeyPair.generateNew();
		var builder = TxLowLevelBuilder.newBuilder();
		var upToken = createToken(keyPair, builder);
		spendToken(builder, upToken, 2);
		var atom = builder.signAndBuild(keyPair::sign);

		// Act/Assert
		assertThatThrownBy(() -> sut.execute(List.of(atom))).isInstanceOf(RadixEngineException.class);
	}
}
