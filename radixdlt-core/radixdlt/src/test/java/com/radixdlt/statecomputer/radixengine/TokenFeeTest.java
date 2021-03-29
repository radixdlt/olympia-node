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
import com.google.inject.multibindings.ProvidesIntoSet;
import com.google.inject.name.Named;
import com.google.inject.name.Names;
import com.radixdlt.DefaultSerialization;
import com.radixdlt.SingleNodeAndPeersDeterministicNetworkModule;
import com.radixdlt.application.TokenUnitConversions;
import com.radixdlt.atom.Atom;
import com.radixdlt.atom.TxBuilder;
import com.radixdlt.atom.TxLowLevelBuilder;
import com.radixdlt.atommodel.tokens.TokenDefinitionUtils;
import com.radixdlt.consensus.LedgerProof;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.constraintmachine.REInstruction;
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.constraintmachine.Spin;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.engine.RadixEngine;
import com.radixdlt.engine.RadixEngineException;
import com.radixdlt.fees.NativeToken;
import com.radixdlt.identifiers.RRI;
import com.radixdlt.identifiers.RadixAddress;
import com.radixdlt.mempool.MempoolMaxSize;
import com.radixdlt.mempool.MempoolThrottleMs;
import com.radixdlt.serialization.DeserializeException;
import com.radixdlt.statecomputer.EpochCeilingView;
import com.radixdlt.statecomputer.LedgerAndBFTProof;
import com.radixdlt.statecomputer.checkpoint.Genesis;
import com.radixdlt.statecomputer.checkpoint.MockedGenesisAtomModule;
import com.radixdlt.statecomputer.transaction.TokenFeeModule;
import com.radixdlt.store.DatabaseLocation;
import com.radixdlt.store.LastStoredProof;
import com.radixdlt.utils.UInt256;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.radix.TokenIssuance;

import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class TokenFeeTest {

	@Rule
	public TemporaryFolder folder = new TemporaryFolder();

	@Inject
	private RadixEngine<LedgerAndBFTProof> sut;

	private ECKeyPair ecKeyPair = ECKeyPair.generateNew();

	private RadixAddress address;

	@Inject
	@NativeToken
	private RRI nativeToken;

	@Inject
	@Genesis
	private List<Atom> genesis;

	@Inject
	@Named("magic")
	private int magic;

	// FIXME: Hack, need this in order to cause provider for genesis to be stored
	@Inject
	@LastStoredProof
	private LedgerProof ledgerProof;

	private List<Particle> particles;

	private final UInt256 fee = UInt256.TEN.pow(TokenDefinitionUtils.SUB_UNITS_POW_10 - 3).multiply(UInt256.from(50));

	private Injector createInjector() {
		return Guice.createInjector(
			new SingleNodeAndPeersDeterministicNetworkModule(),
			new TokenFeeModule(),
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

				@ProvidesIntoSet
				private TokenIssuance mempoolFillerIssuance() {
					return TokenIssuance.of(ecKeyPair.getPublicKey(), TokenUnitConversions.unitsToSubunits(10000000000L));
				}
			}
		);
	}

	@Before
	public void setup() {
		createInjector().injectMembers(this);
		this.address = new RadixAddress((byte) magic, ecKeyPair.getPublicKey());
		particles = genesis.stream()
			.flatMap(Atom::uniqueInstructions)
			.filter(i -> i.getNextSpin() == Spin.UP)
			.map(REInstruction::getData)
			.map(d -> {
				try {
					return DefaultSerialization.getInstance().fromDson(d, Particle.class);
				} catch (DeserializeException e) {
					throw new IllegalStateException();
				}
			})
			.collect(Collectors.toList());
	}

	@Test
	public void when_validating_atom_with_particles__result_has_no_error() throws Exception {
		var atom = TxBuilder.newBuilder(address, particles)
			.mutex("test")
			.burnForFee(nativeToken, fee)
			.signAndBuild(ecKeyPair::sign);

		sut.execute(List.of(atom));
	}

	@Test
	public void when_validating_atom_without_particles__result_has_error() {
		var atom = TxLowLevelBuilder.newBuilder().buildWithoutSignature();
		assertThatThrownBy(() -> sut.execute(List.of(atom)))
			.isInstanceOf(RadixEngineException.class);
	}


	@Test
	public void when_validating_atom_with_fee_and_no_change__result_has_no_error() throws Exception {
		var atom = TxBuilder.newBuilder(address, particles)
			.burnForFee(nativeToken, fee)
			.signAndBuild(ecKeyPair::sign);

		sut.execute(List.of(atom));
	}
}
