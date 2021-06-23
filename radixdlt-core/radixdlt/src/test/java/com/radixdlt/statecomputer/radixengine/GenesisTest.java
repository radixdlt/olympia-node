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
import com.radixdlt.atom.MutableTokenDefinition;
import com.radixdlt.atommodel.tokens.state.TokenResource;
import com.radixdlt.consensus.LedgerProof;
import com.radixdlt.engine.parser.REParser;
import com.radixdlt.fees.NativeToken;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.mempool.MempoolConfig;
import com.radixdlt.qualifier.NumPeers;
import com.radixdlt.statecomputer.RadixEngineConfig;
import com.radixdlt.statecomputer.checkpoint.MockedGenesisModule;
import com.radixdlt.statecomputer.forks.BetanetForkConfigsModule;
import com.radixdlt.statecomputer.forks.RadixEngineForksLatestOnlyModule;
import com.radixdlt.store.DatabaseLocation;
import com.radixdlt.store.LastStoredProof;
import com.radixdlt.store.ReadableAddrsStore;

import static org.assertj.core.api.Assertions.assertThat;

public class GenesisTest {

	@Rule
	public TemporaryFolder folder = new TemporaryFolder();

	@Inject
	@NativeToken
	private MutableTokenDefinition xrd;

	// FIXME: Hack, need this in order to cause provider for genesis to be stored
	@Inject
	@LastStoredProof
	private LedgerProof ledgerProof;

	@Inject
	private ReadableAddrsStore readableAddrs;

	@Inject
	private REParser parser;

	private Injector createInjector() {
		return Guice.createInjector(
			MempoolConfig.asModule(1000, 10),
			new BetanetForkConfigsModule(),
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

	@Test
	public void xrd_token_should_match_initial_setup() throws Exception {
		// Arrange
		createInjector().injectMembers(this);

		var p = readableAddrs.loadAddr(null, REAddr.ofNativeToken(), parser.getSubstateDeserialization());
		assertThat(p)
			.hasValueSatisfying(particle -> {
				var tok = (TokenResource) particle;
				assertThat(tok.getIconUrl()).isEqualTo(xrd.getIconUrl());
				assertThat(tok.getUrl()).isEqualTo(xrd.getTokenUrl());
			});
	}
}
