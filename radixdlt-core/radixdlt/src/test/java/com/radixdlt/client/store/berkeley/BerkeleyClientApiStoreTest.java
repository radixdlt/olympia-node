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

import com.radixdlt.atom.Substate;
import com.radixdlt.atom.SubstateId;
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.constraintmachine.REInstruction;
import com.radixdlt.constraintmachine.Spin;
import com.radixdlt.store.berkeley.BerkeleyLedgerEntryStore;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.name.Names;
import com.radixdlt.DefaultSerialization;
import com.radixdlt.constraintmachine.ParsedInstruction;
import com.radixdlt.atommodel.tokens.FixedSupplyTokenDefinitionParticle;
import com.radixdlt.atommodel.tokens.MutableSupplyTokenDefinitionParticle;
import com.radixdlt.atommodel.tokens.StakedTokensParticle;
import com.radixdlt.atommodel.tokens.TransferrableTokensParticle;
import com.radixdlt.atommodel.tokens.UnallocatedTokensParticle;
import com.radixdlt.SingleNodeAndPeersDeterministicNetworkModule;
import com.radixdlt.atom.Atom;
import com.radixdlt.atom.FixedTokenDefinition;
import com.radixdlt.atom.MutableTokenDefinition;
import com.radixdlt.atom.TxBuilder;
import com.radixdlt.atom.TxBuilderException;
import com.radixdlt.atom.TxLowLevelBuilder;
import com.radixdlt.atommodel.tokens.TokenDefinitionSubstate;
import com.radixdlt.atommodel.tokens.TokenPermission;
import com.radixdlt.client.store.TokenDefinitionRecord;
import com.radixdlt.client.store.TransactionParser;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.constraintmachine.ParsedTransaction;
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.constraintmachine.PermissionLevel;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.HashUtils;
import com.radixdlt.engine.RadixEngine;
import com.radixdlt.engine.RadixEngineException;
import com.radixdlt.environment.ScheduledEventDispatcher;
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
import com.radixdlt.store.DatabaseEnvironment;
import com.radixdlt.store.DatabaseLocation;
import com.radixdlt.store.berkeley.BerkeleyLedgerEntryStore;
import com.radixdlt.store.berkeley.FullTransaction;
import com.radixdlt.utils.UInt256;
import com.radixdlt.utils.functional.Result;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.disposables.Disposable;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class BerkeleyClientApiStoreTest {
	private static final ECKeyPair OWNER_KEYPAIR = ECKeyPair.generateNew();
	private static final RadixAddress OWNER = new RadixAddress((byte) 0, OWNER_KEYPAIR.getPublicKey());
	private static final ECKeyPair TOKEN_KEYPAIR = ECKeyPair.generateNew();
	private static final RadixAddress TOKEN_ADDRESS = new RadixAddress((byte) 0, TOKEN_KEYPAIR.getPublicKey());

	private static final RRI TOKEN = RRI.of(TOKEN_ADDRESS, "COFFEE");

	private final Serialization serialization = DefaultSerialization.getInstance();
	private final BerkeleyLedgerEntryStore ledgerStore = mock(BerkeleyLedgerEntryStore.class);

	private DatabaseEnvironment environment;

	@Rule
	public TemporaryFolder folder = new TemporaryFolder();

	@Inject
	private RadixEngine<LedgerAndBFTProof> engine;

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
		environment = new DatabaseEnvironment(folder.getRoot().getAbsolutePath(), 0);
		var injector = createInjector();
		injector.injectMembers(this);
	}

	@Test
	public void tokenBalancesAreReturned() throws TxBuilderException, RadixEngineException {
		var tokenDef = prepareMutableTokenDef(TOKEN.getName());
		var tx = TxBuilder.newBuilder(TOKEN.getAddress())
			.createMutableToken(tokenDef)
			.mint(TOKEN, TOKEN_ADDRESS, UInt256.EIGHT)
			.burn(TOKEN, UInt256.ONE)
			.burnForFee(TOKEN, UInt256.ONE)
			.transfer(TOKEN, OWNER, UInt256.FOUR)
			.signAndBuild(TOKEN_KEYPAIR::sign);

		var clientApiStore = prepareApiStore(TOKEN_KEYPAIR, tx);

		clientApiStore.getTokenBalances(TOKEN.getAddress())
			.onSuccess(list -> {
				assertEquals(1, list.size());
				assertEquals(UInt256.TWO, list.get(0).getAmount());
				assertEquals(TOKEN, list.get(0).getRri());
			})
			.onFailureDo(() -> fail("Failure is not expected here"));

		clientApiStore.getTokenBalances(OWNER)
			.onSuccess(list -> {
				assertEquals(1, list.size());
				assertEquals(UInt256.FOUR, list.get(0).getAmount());
				assertEquals(TOKEN, list.get(0).getRri());
			})
			.onFailureDo(() -> fail("Failure is not expected here"));
	}

	@Test
	public void tokenSupplyIsCalculateProperlyForInitialTokenIssuance() throws TxBuilderException, RadixEngineException {
		var tokenDef = prepareMutableTokenDef(TOKEN.getName());
		var tx = TxBuilder.newBuilder(TOKEN.getAddress())
			.createMutableToken(tokenDef)
			.signAndBuild(TOKEN_KEYPAIR::sign);

		var clientApiStore = prepareApiStore(TOKEN_KEYPAIR, tx);

		clientApiStore.getTokenSupply(TOKEN)
			.onSuccess(amount -> assertEquals(UInt256.ZERO, amount))
			.onFailure(this::failWithMessage);
	}

	@Test
	public void tokenSupplyIsCalculateProperlyAfterBurnMint() throws TxBuilderException, RadixEngineException {
		var tokenDef = prepareMutableTokenDef(TOKEN.getName());
		var tx = TxBuilder.newBuilder(TOKEN.getAddress())
			.createMutableToken(tokenDef)
			.mint(TOKEN, TOKEN_ADDRESS, UInt256.TEN)
			.burn(TOKEN, UInt256.ONE)
			.burnForFee(TOKEN, UInt256.ONE)
			.signAndBuild(TOKEN_KEYPAIR::sign);

		var clientApiStore = prepareApiStore(TOKEN_KEYPAIR, tx);

		clientApiStore.getTokenSupply(TOKEN)
			.onSuccess(amount -> assertEquals(UInt256.EIGHT, amount))
			.onFailure(this::failWithMessage);
	}

	@Test
	public void mutableTokenDefinitionIsStoredAndAccessible() throws TxBuilderException, RadixEngineException {
		var fooDef = new AtomicReference<TokenDefinitionRecord>();
		var tokenDef = prepareMutableTokenDef(TOKEN.getName());
		var tx = TxBuilder.newBuilder(TOKEN.getAddress())
			.createMutableToken(tokenDef)
			.signAndBuild(TOKEN_KEYPAIR::sign, i -> extractTokenDefinition(i).ifPresent(fooDef::set));

		var clientApiStore = prepareApiStore(TOKEN_KEYPAIR, tx);

		clientApiStore.getTokenDefinition(TOKEN)
			.onSuccess(tokDef -> assertEquals(fooDef.get(), tokDef))
			.onFailure(this::failWithMessage);
	}

	@Test
	public void fixedTokenDefinitionIsStoredAndAccessible() throws TxBuilderException, RadixEngineException {
		var fooDef = new AtomicReference<TokenDefinitionRecord>();
		var tokenDef = prepareFixedTokenDef();
		var tx = TxBuilder.newBuilder(TOKEN.getAddress())
			.createFixedToken(tokenDef)
			.signAndBuild(TOKEN_KEYPAIR::sign, i -> extractTokenDefinition(i).ifPresent(fooDef::set));

		var clientApiStore = prepareApiStore(TOKEN_KEYPAIR, tx);

		clientApiStore.getTokenDefinition(TOKEN)
			.onSuccess(tokDef -> assertEquals(fooDef.get(), tokDef))
			.onFailure(this::failWithMessage);
	}

	@SuppressWarnings("unchecked")
	private BerkeleyClientApiStore prepareApiStore(ECKeyPair keyPair, Atom... tx) throws RadixEngineException {
		var transactions = engine.execute(List.of(tx), null, PermissionLevel.USER)
			.stream()
			.map(parsedTransaction -> parsedToFull(keyPair, parsedTransaction))
			.collect(Collectors.toList());

		//Insert necessary values on DB rebuild
		doAnswer(invocation -> {
			transactions.forEach(invocation.<Consumer<FullTransaction>>getArgument(0));
			return null;
		}).when(ledgerStore).forEach(any(Consumer.class));

		var ledgerCommitted = mock(Observable.class);
		when(ledgerCommitted.observeOn(any())).thenReturn(ledgerCommitted);

		var disposable = mock(Disposable.class);
		when(ledgerCommitted.subscribe((io.reactivex.rxjava3.functions.Consumer<?>) any())).thenReturn(disposable);

		var transactionParser = mock(TransactionParser.class);

		return new BerkeleyClientApiStore(
			environment,
			ledgerStore,
			serialization,
			mock(SystemCounters.class),
			mock(ScheduledEventDispatcher.class),
			ledgerCommitted,
			0,
			transactionParser
		);
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

	private void failWithMessage(com.radixdlt.utils.functional.Failure failure) {
		Assert.fail(failure.message());
	}

	private Optional<TokenDefinitionRecord> extractTokenDefinition(Iterable<Particle> iterable) {
		return StreamSupport.stream(iterable.spliterator(), false)
			.filter(TokenDefinitionSubstate.class::isInstance)
			.map(TokenDefinitionSubstate.class::cast)
			.map(TokenDefinitionRecord::from)
			.findFirst()
			.flatMap(Result::toOptional);
	}

	private MutableTokenDefinition prepareMutableTokenDef(String symbol) {
		return new MutableTokenDefinition(
			symbol, symbol, description(symbol), iconUrl(symbol), homeUrl(symbol), UInt256.ONE,
			Map.of(
				TokenTransition.BURN, TokenPermission.ALL,
				TokenTransition.MINT, TokenPermission.TOKEN_OWNER_ONLY
			)
		);
	}

	private FixedTokenDefinition prepareFixedTokenDef() {
		var symbol = TOKEN.getName();

		return new FixedTokenDefinition(
			symbol, symbol, description(symbol), iconUrl(symbol), homeUrl(symbol), UInt256.ONE
		);
	}

	private String description(String symbol) {
		return "Token with symbol " + symbol;
	}

	private String iconUrl(String symbol) {
		return "https://" + symbol.toLowerCase(Locale.US) + ".coin.com/icon";
	}

	private String homeUrl(String symbol) {
		return "https://" + symbol.toLowerCase(Locale.US) + ".coin.com/home";
	}
}
