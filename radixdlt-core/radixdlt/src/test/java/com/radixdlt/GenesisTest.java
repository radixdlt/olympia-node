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

package com.radixdlt;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Scopes;
import com.radixdlt.atom.Txn;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.counters.SystemCountersImpl;
import com.radixdlt.engine.RadixEngineException;
import com.radixdlt.ledger.LedgerAccumulator;
import com.radixdlt.ledger.SimpleLedgerAccumulatorAndVerifier;
import com.radixdlt.networks.Network;
import com.radixdlt.statecomputer.checkpoint.GenesisBuilder;
import com.radixdlt.statecomputer.forks.ForksModule;
import com.radixdlt.statecomputer.forks.MainnetForksModule;
import com.radixdlt.utils.Bytes;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.Collection;
import java.util.stream.Collectors;

@RunWith(Parameterized.class)
public class GenesisTest {
	@Parameterized.Parameters
	public static Collection<Object[]> parameters() {
		return Arrays.stream(Network.values())
			.flatMap(n -> n.genesisTxn().stream())
			.map(Bytes::fromHexString)
			.map(Txn::create)
			.map(txn -> new Object[] {txn})
			.collect(Collectors.toList());
	}

	private final Txn genesis;

	@Inject
	private GenesisBuilder genesisBuilder;

	public GenesisTest(Txn genesis) {
		this.genesis = genesis;
	}

	@Test
	public void genesis_should_be_a_valid_transaction() throws RadixEngineException {
		Guice.createInjector(
			new ForksModule(),
			new MainnetForksModule(),
			new CryptoModule(),
			new AbstractModule() {
				@Override
				public void configure() {
					bind(SystemCounters.class).to(SystemCountersImpl.class).in(Scopes.SINGLETON);
					bind(LedgerAccumulator.class).to(SimpleLedgerAccumulatorAndVerifier.class);
				}
			}
		).injectMembers(this);
		genesisBuilder.generateGenesisProof(genesis);
	}
}
