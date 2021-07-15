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

import com.google.common.base.Stopwatch;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.radixdlt.SingleNodeAndPeersDeterministicNetworkModule;
import com.radixdlt.application.system.NextValidatorSetEvent;
import com.radixdlt.application.tokens.Amount;
import com.radixdlt.atom.TxBuilderException;
import com.radixdlt.atom.TxnConstructionRequest;
import com.radixdlt.atom.actions.MintToken;
import com.radixdlt.atom.actions.NextEpoch;
import com.radixdlt.atom.actions.NextRound;
import com.radixdlt.atom.actions.RegisterValidator;
import com.radixdlt.atom.actions.StakeTokens;
import com.radixdlt.consensus.LedgerHeader;
import com.radixdlt.consensus.LedgerProof;
import com.radixdlt.consensus.TimestampedECDSASignatures;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.BFTValidator;
import com.radixdlt.consensus.bft.BFTValidatorSet;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.constraintmachine.PermissionLevel;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.HashUtils;
import com.radixdlt.engine.RadixEngine;
import com.radixdlt.engine.RadixEngineException;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.ledger.AccumulatorState;
import com.radixdlt.mempool.MempoolConfig;
import com.radixdlt.qualifier.NumPeers;
import com.radixdlt.statecomputer.LedgerAndBFTProof;
import com.radixdlt.statecomputer.checkpoint.MockedGenesisModule;
import com.radixdlt.statecomputer.forks.ForksModule;
import com.radixdlt.statecomputer.forks.MainnetForkConfigsModule;
import com.radixdlt.statecomputer.forks.RadixEngineForksLatestOnlyModule;
import com.radixdlt.store.DatabaseLocation;
import com.radixdlt.store.LastStoredProof;
import com.radixdlt.utils.PrivateKeys;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

public class LargeEpochChangeTest {
	private static final Logger logger = LogManager.getLogger();
	private static final ECKeyPair TEST_KEY = PrivateKeys.ofNumeric(1);
	@Rule
	public TemporaryFolder folder = new TemporaryFolder();

	@Inject
	private RadixEngine<LedgerAndBFTProof> sut;

	// FIXME: Hack, need this in order to cause provider for genesis to be stored
	@Inject
	@LastStoredProof
	private LedgerProof ledgerProof;

	private Injector createInjector() {
		return Guice.createInjector(
			MempoolConfig.asModule(1000, 10),
			new MainnetForkConfigsModule(),
			new RadixEngineForksLatestOnlyModule(),
			new ForksModule(),
			new SingleNodeAndPeersDeterministicNetworkModule(TEST_KEY),
			new MockedGenesisModule(
				Set.of(TEST_KEY.getPublicKey()),
				Amount.ofTokens(100000),
				Amount.ofTokens(1000)
			),
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
	public void large_epoch() throws Exception {
		long numStakes = 10000 * 3;

		createInjector().injectMembers(this);
		// Arrange
		var request = TxnConstructionRequest.create();
		IntStream.range(1, (int) numStakes + 1)
			.forEach(i -> {
				var k = PrivateKeys.ofNumeric(i);
				var addr = REAddr.ofPubKeyAccount(k.getPublicKey());
				request.action(new MintToken(REAddr.ofNativeToken(), addr, Amount.ofTokens(numStakes * 100).toSubunits()));
				request.action(new RegisterValidator(k.getPublicKey()));
			});
		var mint = sut.construct(request).buildWithoutSignature();
		logger.info("mint_txn_size={}", mint.getPayload().length);
		var accumulator = new AccumulatorState(2, HashUtils.zero256());
		var proof = new LedgerProof(HashUtils.zero256(), LedgerHeader.create(1, View.of(1), accumulator, 0), new TimestampedECDSASignatures());
		sut.execute(List.of(mint), LedgerAndBFTProof.create(proof), PermissionLevel.SYSTEM);

		var construction = Stopwatch.createUnstarted();
		var execution = Stopwatch.createUnstarted();

		IntStream.range(1, (int) numStakes + 1)
			.forEach(i -> {
				try {
					if (i % 1000 == 0) {
						logger.info("Staking {}/{} Construction: {}s Execution: {}s", i, numStakes,
							construction.elapsed(TimeUnit.SECONDS),
							execution.elapsed(TimeUnit.SECONDS)
						);
					}
					var k = PrivateKeys.ofNumeric(i);
					var addr = REAddr.ofPubKeyAccount(k.getPublicKey());
					construction.start();
					var txn = sut.construct(new StakeTokens(addr, k.getPublicKey(), Amount.ofTokens(10 + i).toSubunits())).buildWithoutSignature();
					construction.stop();
					var acc = new AccumulatorState(2 + i, HashUtils.zero256());
					var proof2 = new LedgerProof(HashUtils.zero256(), LedgerHeader.create(1, View.of(1), acc, 0), new TimestampedECDSASignatures());
					execution.start();
					sut.execute(List.of(txn), LedgerAndBFTProof.create(proof2), PermissionLevel.SYSTEM);
					execution.stop();
				} catch (TxBuilderException | RadixEngineException e) {
					logger.error("key {}", i);
					e.printStackTrace();
				}
			});

		// Act
		logger.info("preparing...");
		var txn = sut.construct(TxnConstructionRequest.create()
			.action(new NextRound(10, true, 0, v -> TEST_KEY.getPublicKey()))
			.action(new NextEpoch(1))
		).buildWithoutSignature();
		var result = sut.transientBranch().execute(List.of(txn), PermissionLevel.SUPER_USER);
		sut.deleteBranches();
		var nextValidatorSet = result.get(0).getEvents().stream()
			.filter(NextValidatorSetEvent.class::isInstance)
			.map(NextValidatorSetEvent.class::cast)
			.findFirst()
			.map(e -> BFTValidatorSet.from(
				e.nextValidators().stream()
					.map(v -> BFTValidator.from(BFTNode.create(v.getValidatorKey()), v.getAmount())))
			);
		var stateUpdates = result.get(0).stateUpdates().count();
		logger.info("epoch_txn size={} state_updates={}", txn.getPayload().length, stateUpdates);
		var acc = new AccumulatorState(2 + 1 + numStakes, HashUtils.zero256());
		var header = LedgerHeader.create(1, View.of(10), acc, 0, nextValidatorSet.orElseThrow());
		var proof2 = new LedgerProof(HashUtils.zero256(), header, new TimestampedECDSASignatures());
		var result2 = this.sut.execute(List.of(txn), LedgerAndBFTProof.create(proof2), PermissionLevel.SUPER_USER);
		logger.info("executed...");
		for (var v : nextValidatorSet.orElseThrow().getValidators()) {
			logger.info("validator {} {}", v.getNode(), v.getPower());
		}
	}
}
