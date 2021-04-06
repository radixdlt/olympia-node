/*
 * (C) Copyright 2021 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 */
package com.radixdlt.client.store.berkeley;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.radix.api.jsonrpc.ActionType;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.name.Names;
import com.radixdlt.SingleNodeAndPeersDeterministicNetworkModule;
import com.radixdlt.atom.MutableTokenDefinition;
import com.radixdlt.atom.TxBuilder;
import com.radixdlt.atom.Txn;
import com.radixdlt.client.store.ActionEntry;
import com.radixdlt.client.store.ParsedTx;
import com.radixdlt.client.store.ParticleWithSpin;
import com.radixdlt.client.store.TransactionParser;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.constraintmachine.PermissionLevel;
import com.radixdlt.constraintmachine.RETxn;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.engine.RadixEngine;
import com.radixdlt.engine.RadixEngineException;
import com.radixdlt.identifiers.RRI;
import com.radixdlt.identifiers.RadixAddress;
import com.radixdlt.mempool.MempoolConfig;
import com.radixdlt.serialization.Serialization;
import com.radixdlt.statecomputer.EpochCeilingView;
import com.radixdlt.statecomputer.LedgerAndBFTProof;
import com.radixdlt.statecomputer.checkpoint.MockedGenesisAtomModule;
import com.radixdlt.store.DatabaseLocation;
import com.radixdlt.store.EngineStore;
import com.radixdlt.utils.UInt256;
import com.radixdlt.utils.functional.Result;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;

import static com.radixdlt.client.ClientApiUtils.extractCreator;

public class TransactionParserTest {
	private static final byte MAGIC = (byte) 0;
	private final ECKeyPair tokenOwnerKeyPair = ECKeyPair.generateNew();
	private final RadixAddress tokenOwnerAddress = new RadixAddress(MAGIC, tokenOwnerKeyPair.getPublicKey());
	private final ECKeyPair validatorKeyPair = ECKeyPair.generateNew();
	private final RadixAddress validatorAddress = new RadixAddress((byte) 0, validatorKeyPair.getPublicKey());

	private final RRI tokenRri = RRI.of(tokenOwnerAddress, "TEST");
	private final MutableTokenDefinition tokDef =
		new MutableTokenDefinition("TEST", "Test", "description", null, null);

	@Rule
	public TemporaryFolder folder = new TemporaryFolder();

	@Inject
	private RadixEngine<LedgerAndBFTProof> engine;
	@Inject
	private Serialization serialization;
	@Inject
	private EngineStore<LedgerAndBFTProof> store;

	private Injector createInjector() {
		return Guice.createInjector(
			new SingleNodeAndPeersDeterministicNetworkModule(),
			new MockedGenesisAtomModule(),
			new AbstractModule() {
				@Override
				protected void configure() {
					bindConstant().annotatedWith(Names.named("numPeers")).to(0);
					bind(MempoolConfig.class).toInstance(MempoolConfig.of(1000L, 0L));
					bindConstant().annotatedWith(DatabaseLocation.class).to(folder.getRoot().getAbsolutePath());
					bind(View.class).annotatedWith(EpochCeilingView.class).toInstance(View.of(100));
				}
			}
		);
	}

	@Before
	public void setUp() {
		var injector = createInjector();
		injector.injectMembers(this);
	}

	@Test
	public void typicalPatternsAreDetectedProperly() throws Exception {
		var tokDefBuilder = TxBuilder.newBuilder(tokenOwnerAddress)
			.createMutableToken(tokDef)
			.mint(tokenRri, tokenOwnerAddress, UInt256.TEN);
		var atom0 = tokDefBuilder.signAndBuild(tokenOwnerKeyPair::sign);

		var validatorBuilder = TxBuilder.newBuilder(validatorAddress)
			.registerAsValidator();
		var atom1 = validatorBuilder.signAndBuild(validatorKeyPair::sign);

		executeAndDecode(List.of(), UInt256.ZERO, atom0, atom1);

		var atom2 = TxBuilder.newBuilder(tokenOwnerAddress, store)
			.stakeTo(tokenRri, validatorAddress, UInt256.FIVE)
			.signAndBuild(tokenOwnerKeyPair::sign);

		executeAndDecode(List.of(ActionType.STAKE), UInt256.ZERO, atom2);

		var atom3 = TxBuilder.newBuilder(tokenOwnerAddress, store)
			.unstakeFrom(tokenRri, validatorAddress, UInt256.ONE)
			.signAndBuild(tokenOwnerKeyPair::sign);

		executeAndDecode(List.of(ActionType.UNSTAKE), UInt256.ZERO, atom3);

		var atom4 = TxBuilder.newBuilder(tokenOwnerAddress, store)
			.transfer(tokenRri, validatorAddress, UInt256.TWO)
			.burnForFee(tokenRri, UInt256.THREE)
			.signAndBuild(tokenOwnerKeyPair::sign);

		executeAndDecode(List.of(ActionType.TRANSFER), UInt256.THREE, atom4);
	}

	private void executeAndDecode(
		List<ActionType> expectedActions, UInt256 fee, Txn... txns
	) throws RadixEngineException, InterruptedException {
		var list = engine.execute(List.of(txns), null, PermissionLevel.USER);

		// Wait for propagation
		Thread.sleep(100L);

		if (txns.length != 1) {
			return;
		}

		list.stream()
			.map(this::toParsedTx)
			.map(result -> result.flatMap(parsedTx -> TransactionParser.parse(tokenOwnerAddress, parsedTx, Instant.now())))
			.forEach(entry -> {
				entry
					.onFailureDo(Assert::fail)
					.onSuccess(historyEntry -> assertEquals(fee, historyEntry.getFee()))
					.map(this::toActionTypes)
					.onSuccess(types -> assertEquals(expectedActions, types));
			});
	}

	private Result<ParsedTx> toParsedTx(RETxn reTxn) {
		var particles = reTxn.instructions()
			.stream()
			.map(ParticleWithSpin::create)
			.collect(Collectors.toList());

		return ParsedTx.create(reTxn.getTxn().getId(), particles, Optional.empty(), extractCreator(reTxn.getTxn(), MAGIC));
	}

	private List<ActionType> toActionTypes(com.radixdlt.client.store.TxHistoryEntry txEntry) {
		return txEntry.getActions()
			.stream()
			.map(ActionEntry::getType)
			.collect(Collectors.toList());
	}
}
