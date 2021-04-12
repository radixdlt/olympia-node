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

import com.radixdlt.constraintmachine.ConstraintMachine;
import com.radixdlt.constraintmachine.REParsedInstruction;
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
import com.radixdlt.SingleNodeAndPeersDeterministicNetworkModule;
import com.radixdlt.atom.FixedTokenDefinition;
import com.radixdlt.atom.MutableTokenDefinition;
import com.radixdlt.atom.TxBuilder;
import com.radixdlt.atom.TxBuilderException;
import com.radixdlt.atom.TxLowLevelBuilder;
import com.radixdlt.atom.Txn;
import com.radixdlt.atommodel.tokens.FixedSupplyTokenDefinitionParticle;
import com.radixdlt.atommodel.tokens.MutableSupplyTokenDefinitionParticle;
import com.radixdlt.atommodel.tokens.TokenDefinitionSubstate;
import com.radixdlt.client.store.TokenDefinitionRecord;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.constraintmachine.PermissionLevel;
import com.radixdlt.constraintmachine.REParsedTxn;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.engine.RadixEngine;
import com.radixdlt.engine.RadixEngineException;
import com.radixdlt.environment.ScheduledEventDispatcher;
import com.radixdlt.identifiers.AID;
import com.radixdlt.identifiers.RRI;
import com.radixdlt.identifiers.RadixAddress;
import com.radixdlt.mempool.MempoolConfig;
import com.radixdlt.serialization.Serialization;
import com.radixdlt.statecomputer.EpochCeilingView;
import com.radixdlt.statecomputer.LedgerAndBFTProof;
import com.radixdlt.statecomputer.checkpoint.MockedGenesisAtomModule;
import com.radixdlt.store.DatabaseEnvironment;
import com.radixdlt.store.DatabaseLocation;
import com.radixdlt.store.berkeley.BerkeleyLedgerEntryStore;
import com.radixdlt.utils.UInt256;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

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
	private ConstraintMachine constraintMachine;

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
					bind(MempoolConfig.class).toInstance(MempoolConfig.of(1000L, 0L));
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
		var tx = TxBuilder.newBuilder(TOKEN_ADDRESS)
			.createMutableToken(tokenDef)
			.mint(TOKEN, TOKEN_ADDRESS, UInt256.EIGHT)
			.burn(TOKEN, UInt256.ONE)
			.transfer(TOKEN, OWNER, UInt256.FOUR)
			.signAndBuild(TOKEN_KEYPAIR::sign);

		var clientApiStore = prepareApiStore(TOKEN_KEYPAIR, tx);

		clientApiStore.getTokenBalances(TOKEN_ADDRESS)
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
		var tx = TxBuilder.newBuilder(TOKEN_ADDRESS)
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
		var tx = TxBuilder.newBuilder(TOKEN_ADDRESS)
			.createMutableToken(tokenDef)
			.mint(TOKEN, TOKEN_ADDRESS, UInt256.TEN)
			.burn(TOKEN, UInt256.ONE)
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
		var tx = TxBuilder.newBuilder(TOKEN_ADDRESS)
			.createMutableToken(tokenDef)
			.signAndBuild(TOKEN_KEYPAIR::sign, store -> {
				try (var cursor = store.openIndexedCursor(MutableSupplyTokenDefinitionParticle.class)) {
					while (cursor.hasNext()) {
						toTokenDefinitionRecord(cursor.next().getParticle()).ifPresent(fooDef::set);
					}
				}
			});

		var clientApiStore = prepareApiStore(TOKEN_KEYPAIR, tx);

		clientApiStore.getTokenDefinition(TOKEN)
			.onSuccess(tokDef -> assertEquals(fooDef.get(), tokDef))
			.onFailure(this::failWithMessage);
	}

	@Test
	public void fixedTokenDefinitionIsStoredAndAccessible() throws TxBuilderException, RadixEngineException {
		var fooDef = new AtomicReference<TokenDefinitionRecord>();
		var tokenDef = prepareFixedTokenDef();
		var tx = TxBuilder.newBuilder(TOKEN_ADDRESS)
			.createFixedToken(tokenDef)
			.signAndBuild(TOKEN_KEYPAIR::sign, store -> {
				try (var cursor = store.openIndexedCursor(FixedSupplyTokenDefinitionParticle.class)) {
					while (cursor.hasNext()) {
						toTokenDefinitionRecord(cursor.next().getParticle()).ifPresent(fooDef::set);
					}
				}
			});

		var clientApiStore = prepareApiStore(TOKEN_KEYPAIR, tx);

		clientApiStore.getTokenDefinition(TOKEN)
			.onFailure(this::failWithMessage)
			.onSuccess(tokDef -> assertEquals(fooDef.get(), tokDef));
	}

	/*
	@Test
	public void transactionHistoryIsReturnedInPages() throws TxBuilderException, RadixEngineException {
		var tokenDef = prepareMutableTokenDef(TOKEN.getName());
		var tx = TxBuilder.newBuilder(TOKEN_ADDRESS)
			.createMutableToken(tokenDef)
			.mint(TOKEN, TOKEN_ADDRESS, UInt256.TEN)
			.transfer(TOKEN, OWNER, UInt256.FOUR)
			.signAndBuild(TOKEN_KEYPAIR::sign);

		var clientApiStore = prepareApiStore(TOKEN_KEYPAIR, tx);
		var newCursor = new AtomicReference<Instant>();

		clientApiStore.getTransactionHistory(TOKEN_ADDRESS, 1, Optional.empty())
			.onFailure(this::failWithMessage)
			.onSuccess(list -> {
				assertEquals(1, list.size());

				var entry = list.get(0);

				assertEquals(UInt256.ONE, entry.getFee());
				assertEquals(1, entry.getActions().size());

				var action = entry.getActions().get(0);

				assertEquals(ActionType.TRANSFER, action.getType());
				assertEquals(UInt256.FOUR, action.getAmount());
				assertEquals(TOKEN_ADDRESS, action.getFrom());
				assertEquals(OWNER, action.getTo());

				newCursor.set(entry.timestamp());
			});

		assertNotNull(newCursor.get());

		clientApiStore.getTransactionHistory(TOKEN_ADDRESS, 1, Optional.of(newCursor.get()))
			.onFailure(this::failWithMessage)
			.onSuccess(list -> assertEquals(0, list.size()));
	}

	@Test
	public void incorrectPageSizeIsRejected() throws TxBuilderException, RadixEngineException {
		var tokenDef = prepareMutableTokenDef(TOKEN.getName());
		var tx = TxBuilder.newBuilder(TOKEN_ADDRESS)
			.createMutableToken(tokenDef)
			.mint(TOKEN, TOKEN_ADDRESS, UInt256.TEN)
			.transfer(TOKEN, OWNER, UInt256.FOUR)
			.burn(TOKEN, UInt256.ONE)
			.signAndBuild(TOKEN_KEYPAIR::sign);

		var clientApiStore = prepareApiStore(TOKEN_KEYPAIR, tx);
		clientApiStore.getTransactionHistory(TOKEN_ADDRESS, 0, Optional.empty())
			.onSuccess(list -> fail("Request must be rejected"));
	}
	 */

	@SuppressWarnings("unchecked")
	private BerkeleyClientApiStore prepareApiStore(ECKeyPair keyPair, Txn... tx) throws RadixEngineException {
		var transactions = engine.execute(List.of(tx), null, PermissionLevel.USER)
			.stream()
			.map(REParsedTxn::getTxn)
			.collect(Collectors.toList());

		var txMap = transactions.stream().collect(Collectors.toMap(Txn::getId, Function.identity()));

		when(ledgerStore.get(any(AID.class)))
			.thenAnswer(invocation -> Optional.ofNullable(txMap.get(invocation.getArgument(0, AID.class))));

		//Insert necessary values on DB rebuild
		doAnswer(invocation -> {
			transactions.forEach(invocation.<Consumer<Txn>>getArgument(0));
			return null;
		}).when(ledgerStore).forEach(any(Consumer.class));

		var ledgerCommitted = mock(Observable.class);
		when(ledgerCommitted.observeOn(any())).thenReturn(ledgerCommitted);

		var disposable = mock(Disposable.class);
		when(ledgerCommitted.subscribe((io.reactivex.rxjava3.functions.Consumer<?>) any())).thenReturn(disposable);

		return new BerkeleyClientApiStore(
			environment,
			constraintMachine,
			ledgerStore,
			serialization,
			mock(SystemCounters.class),
			mock(ScheduledEventDispatcher.class),
			ledgerCommitted,
			TOKEN,
			0
		);
	}

	private void failWithMessage(com.radixdlt.utils.functional.Failure failure) {
		Assert.fail(failure.message());
	}

	private Optional<TokenDefinitionRecord> toTokenDefinitionRecord(Particle particle) {
		if (particle instanceof TokenDefinitionSubstate) {
			return TokenDefinitionRecord.from((TokenDefinitionSubstate) particle).toOptional();
		} else {
			return Optional.empty();
		}
	}

	private MutableTokenDefinition prepareMutableTokenDef(String symbol) {
		return new MutableTokenDefinition(
			symbol, symbol, symbol, null, null
		);
	}

	private FixedTokenDefinition prepareFixedTokenDef() {
		var symbol = TOKEN.getName();

		return new FixedTokenDefinition(
			symbol, symbol, symbol, null, null, UInt256.ONE
		);
	}
}
