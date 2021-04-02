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
import com.radixdlt.DefaultSerialization;
import com.radixdlt.SingleNodeAndPeersDeterministicNetworkModule;
import com.radixdlt.atom.Atom;
import com.radixdlt.atom.MutableTokenDefinition;
import com.radixdlt.atom.TxBuilder;
import com.radixdlt.atom.TxBuilderException;
import com.radixdlt.atom.TxLowLevelBuilder;
import com.radixdlt.atommodel.tokens.TokenPermission;
import com.radixdlt.client.store.TransactionParser;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.constraintmachine.ParsedTransaction;
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.constraintmachine.PermissionLevel;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.HashUtils;
import com.radixdlt.engine.RadixEngine;
import com.radixdlt.engine.RadixEngineException;
import com.radixdlt.identifiers.AID;
import com.radixdlt.identifiers.RRI;
import com.radixdlt.identifiers.RadixAddress;
import com.radixdlt.mempool.MempoolMaxSize;
import com.radixdlt.mempool.MempoolThrottleMs;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.Serialization;
import com.radixdlt.statecomputer.EpochCeilingView;
import com.radixdlt.statecomputer.LedgerAndBFTProof;
import com.radixdlt.statecomputer.checkpoint.MockedGenesisAtomModule;
import com.radixdlt.store.DatabaseLocation;
import com.radixdlt.store.berkeley.FullTransaction;
import com.radixdlt.utils.UInt256;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static com.radixdlt.atommodel.tokens.MutableSupplyTokenDefinitionParticle.TokenTransition;
import static com.radixdlt.atommodel.tokens.MutableSupplyTokenDefinitionParticle.TokenTransition.BURN;
import static com.radixdlt.atommodel.tokens.MutableSupplyTokenDefinitionParticle.TokenTransition.MINT;
import static com.radixdlt.atommodel.tokens.TokenPermission.ALL;
import static com.radixdlt.atommodel.tokens.TokenPermission.TOKEN_OWNER_ONLY;

public class TransactionParserTest {
	private final ECKeyPair tokenOwnerKeyPair = ECKeyPair.generateNew();
	private final RadixAddress tokenOwnerAddress = new RadixAddress((byte) 0, tokenOwnerKeyPair.getPublicKey());
	private final ECKeyPair validatorKeyPair = ECKeyPair.generateNew();
	private final RadixAddress validatorAddress = new RadixAddress((byte) 0, validatorKeyPair.getPublicKey());
	private final List<Particle> upParticles = new ArrayList<>();
	private final RRI tokenRri = RRI.of(tokenOwnerAddress, "TEST");
	private final Map<TokenTransition, TokenPermission> permissions = Map.of(BURN, ALL, MINT, TOKEN_OWNER_ONLY);
	private final MutableTokenDefinition tokDef = new MutableTokenDefinition("TEST", "Test", "description", null, null, permissions);

	@Rule
	public TemporaryFolder folder = new TemporaryFolder();

	@Inject
	private RadixEngine<LedgerAndBFTProof> engine;
	@Inject
	private Serialization serialization;

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

	@Before
	public void setUp() {
		var injector = createInjector();
		injector.injectMembers(this);
	}

	@Test
	public void typicalPatternsAreDetectedProperly() throws TxBuilderException, RadixEngineException {
		var tokDefBuilder = TxBuilder.newBuilder(tokenOwnerAddress)
			.createMutableToken(tokDef)
			.mint(tokenRri, tokenOwnerAddress, UInt256.TEN);
		var atom0 = tokDefBuilder.signAndBuild(tokenOwnerKeyPair::sign, u -> u.forEach(upParticles::add));

		var validatorBuilder = TxBuilder.newBuilder(validatorAddress)
			.registerAsValidator();
		var atom1 = validatorBuilder.signAndBuild(validatorKeyPair::sign, u -> u.forEach(upParticles::add));

		executeAndDecode(List.of(), "preparations", atom0, atom1);

		var upSubstate = new AtomicReference<Iterable<Particle>>();
		var atom2 = TxBuilder.newBuilder(tokenOwnerAddress, upParticles)
			.stakeTo(tokenRri, validatorAddress, UInt256.FIVE)
			.signAndBuild(tokenOwnerKeyPair::sign, upSubstate::set);

		executeAndDecode(List.of(), "stake", atom2);

		var atom3 = TxBuilder.newBuilder(tokenOwnerAddress, upSubstate.get())
			.unstakeFrom(tokenRri, validatorAddress, UInt256.ONE)
			.signAndBuild(tokenOwnerKeyPair::sign);

		executeAndDecode(List.of(), "unstake", atom3);

		var atom4 = TxBuilder.newBuilder(tokenOwnerAddress, upSubstate.get())
			.burnForFee(tokenRri, UInt256.ONE)
			.signAndBuild(tokenOwnerKeyPair::sign);

		executeAndDecode(List.of(), "fee", atom4);
	}

	private void executeAndDecode(List<ActionType> expectedActions, String message, Atom... atoms) throws RadixEngineException {
		var list = engine.execute(List.of(atoms), null, PermissionLevel.USER);

		if (atoms.length != 1) {
			return;
		}

		var parser = new TransactionParser(serialization);

		list.stream()
			.map(parsedTransaction -> parsedToFull(tokenOwnerKeyPair, parsedTransaction))
			.map(txWithId -> parser.parse(tokenOwnerAddress, txWithId, Instant.now()))
			.forEach(entry -> System.out.println(entry));
//TODO: missing actions
//			.forEach(action -> {
//				System.out.println();
//				System.out.println("--------------- transaction boundary ---------------");
//				System.out.println(message);
//				action.instructions().forEach(i -> System.out.printf("%s: %s\n", i.getSpin(), i.getParticle().getClass().getSimpleName()));
//			});
	}

	private FullTransaction parsedToFull(ECKeyPair keyPair, ParsedTransaction parsedTransaction) {
		var builder = TxLowLevelBuilder.newBuilder();

		parsedTransaction.instructions().forEach(i -> {
			switch (i.getSpin()) {
				case NEUTRAL:
					break;
				case UP:
					builder.up(i.getParticle());
					break;
				case DOWN:
					builder.virtualDown(i.getParticle());
					break;
			}
		});

		return toFullTransaction(builder.signAndBuild(keyPair::sign));
	}

	private FullTransaction toFullTransaction(Atom tx) {
		var payload = serialization.toDson(tx, DsonOutput.Output.ALL);
		var txId = AID.from(HashUtils.transactionIdHash(payload).asBytes());

		return FullTransaction.create(txId, tx);
	}
}
