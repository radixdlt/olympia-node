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
import com.radixdlt.atom.MutableTokenDefinition;
import com.radixdlt.atom.TxBuilder;
import com.radixdlt.atom.TxBuilderException;
import com.radixdlt.atom.TxLowLevelBuilder;
import com.radixdlt.atommodel.tokens.MutableSupplyTokenDefinitionParticle;
import com.radixdlt.atommodel.tokens.TokenPermission;
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

public class MutableTokenTest {
	private ECKeyPair keyPair = ECKeyPair.generateNew();
	private RadixAddress address = new RadixAddress((byte) 0, keyPair.getPublicKey());

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

	@Test
	public void token_with_no_unallocated_should_fail() {
		createInjector().injectMembers(this);
		var particle = new MutableSupplyTokenDefinitionParticle(
			RRI.of(address, "JOSH"),
			"Joshy Token",
			"Best Token",
			UInt256.ONE,
			null,
			null,
			ImmutableMap.of(
				MutableSupplyTokenDefinitionParticle.TokenTransition.MINT, TokenPermission.TOKEN_OWNER_ONLY,
				MutableSupplyTokenDefinitionParticle.TokenTransition.BURN, TokenPermission.NONE
			)
		);
		var atom = TxLowLevelBuilder.newBuilder()
			.virtualDown(new RRIParticle(RRI.of(address, "JOSH")))
			.up(particle)
			.particleGroup()
			.signAndBuild(keyPair::sign);

		assertThatThrownBy(() -> sut.execute(List.of(atom))).isInstanceOf(RadixEngineException.class);
	}

	@Test
	public void atomic_token_creation_and_spend_should_succeed() throws Exception {
		// Arrange
		createInjector().injectMembers(this);
		var tokDef = new MutableTokenDefinition(
			"TEST",
			"test",
			"desc",
			null,
			null,
			ImmutableMap.of(
				MutableSupplyTokenDefinitionParticle.TokenTransition.BURN, TokenPermission.ALL,
				MutableSupplyTokenDefinitionParticle.TokenTransition.MINT, TokenPermission.TOKEN_OWNER_ONLY
			)
		);
		var atom = TxBuilder.newBuilder(address)
			.createMutableToken(tokDef)
			.mint(RRI.of(address, "TEST"), address, UInt256.SEVEN)
			.transfer(RRI.of(address, "TEST"), address, UInt256.FIVE)
			.signAndBuild(keyPair::sign);

		// Act/Assert
		sut.execute(List.of(atom));
	}

	@Test
	public void can_create_no_description_token() throws TxBuilderException, RadixEngineException {
		// Arrange
		createInjector().injectMembers(this);
		var tokDef = new MutableTokenDefinition(
			"TEST",
			"test",
			null,
			null,
			null,
			ImmutableMap.of(
				MutableSupplyTokenDefinitionParticle.TokenTransition.BURN, TokenPermission.ALL,
				MutableSupplyTokenDefinitionParticle.TokenTransition.MINT, TokenPermission.TOKEN_OWNER_ONLY
			)
		);
		var atom = TxBuilder.newBuilder(address)
			.createMutableToken(tokDef)
			.signAndBuild(keyPair::sign);

		// Act/Assert
		sut.execute(List.of(atom));
	}

	@Test
	public void burn_outside_of_granularity_should_fail() throws Exception {
		// Arrange
		createInjector().injectMembers(this);
		var tokDef = new MutableTokenDefinition(
			"TEST",
			"test",
			"desc",
			null,
			null,
			UInt256.TWO,
			ImmutableMap.of(
				MutableSupplyTokenDefinitionParticle.TokenTransition.BURN, TokenPermission.ALL,
				MutableSupplyTokenDefinitionParticle.TokenTransition.MINT, TokenPermission.TOKEN_OWNER_ONLY
			)
		);
		var atom = TxBuilder.newBuilder(address)
			.createMutableToken(tokDef)
			.mint(RRI.of(address, "TEST"), address, UInt256.EIGHT)
			.burn(RRI.of(address, "TEST"), UInt256.ONE)
			.signAndBuild(keyPair::sign);

		// Act/Assert
		assertThatThrownBy(() -> sut.execute(List.of(atom))).isInstanceOf(RadixEngineException.class);
	}
}
