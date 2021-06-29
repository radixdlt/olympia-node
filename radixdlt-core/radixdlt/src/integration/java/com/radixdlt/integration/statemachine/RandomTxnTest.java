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

package com.radixdlt.integration.statemachine;

import com.radixdlt.statecomputer.forks.ForkManagerModule;
import com.radixdlt.statecomputer.forks.MainnetForksModule;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.radixdlt.SingleNodeAndPeersDeterministicNetworkModule;
import com.radixdlt.atom.Txn;
import com.radixdlt.consensus.LedgerProof;
import com.radixdlt.constraintmachine.PermissionLevel;
import com.radixdlt.engine.RadixEngine;
import com.radixdlt.engine.RadixEngineException;
import com.radixdlt.mempool.MempoolConfig;
import com.radixdlt.qualifier.NumPeers;
import com.radixdlt.statecomputer.LedgerAndBFTProof;
import com.radixdlt.statecomputer.RadixEngineConfig;
import com.radixdlt.statecomputer.checkpoint.MockedGenesisModule;
import com.radixdlt.statecomputer.forks.RadixEngineForksLatestOnlyModule;
import com.radixdlt.store.DatabaseLocation;
import com.radixdlt.store.LastStoredProof;

import java.util.List;
import java.util.Random;

public class RandomTxnTest {
	private static final Logger logger = LogManager.getLogger();
	@Rule
	public TemporaryFolder folder = new TemporaryFolder();

	@Inject
	private RadixEngine<LedgerAndBFTProof> engine;

	// FIXME: Hack, need this in order to cause provider for genesis to be stored
	@Inject
	@LastStoredProof
	private LedgerProof ledgerProof;

	private Injector createInjector() {
		return Guice.createInjector(
			MempoolConfig.asModule(1000, 10),
			new RadixEngineForksLatestOnlyModule(),
			new ForkManagerModule(),
			new MainnetForksModule(),
			RadixEngineConfig.asModule(1, 10),
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
	public void poor_mans_fuzz_test() {
		var random = new Random(12345678);

		// Arrange
		createInjector().injectMembers(this);
		final var count = 1000000;

		for (int i = 0; i < count; i++) {
			int len = random.nextInt(512);
			var payload = new byte[len];
			random.nextBytes(payload);
			for (int j = 0; j < len; j++) {
				payload[j] = random.nextBoolean() ? (byte) random.nextInt(10) : payload[j];
			}
			var txns = List.of(Txn.create(payload));
			if (i % 1000 == 0) {
				logger.info(i + "/" + count);
			}
			try {
				engine.execute(txns, null, PermissionLevel.SYSTEM);
			} catch (RadixEngineException e) {
				// ignore
			}
		}
	}
}
