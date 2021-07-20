/* Copyright 2021 Radix DLT Ltd incorporated in England.
 * 
 * Licensed under the Radix License, Version 1.0 (the "License"); you may not use this
 * file except in compliance with the License. You may obtain a copy of the License at:
 * 
 * radixfoundation.org/licenses/LICENSE-v1
 * 
 * The Licensor hereby grants permission for the Canonical version of the Work to be
 * published, distributed and used under or by reference to the Licensor’s trademark
 * Radix ® and use of any unregistered trade names, logos or get-up.
 * 
 * The Licensor provides the Work (and each Contributor provides its Contributions) on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied,
 * including, without limitation, any warranties or conditions of TITLE, NON-INFRINGEMENT,
 * MERCHANTABILITY, or FITNESS FOR A PARTICULAR PURPOSE.
 * 
 * Whilst the Work is capable of being deployed, used and adopted (instantiated) to create
 * a distributed ledger it is your responsibility to test and validate the code, together
 * with all logic and performance of that code under all foreseeable scenarios.
 * 
 * The Licensor does not make or purport to make and hereby excludes liability for all
 * and any representation, warranty or undertaking in any form whatsoever, whether express
 * or implied, to any entity or person, including any representation, warranty or
 * undertaking, as to the functionality security use, value or other characteristics of
 * any distributed ledger nor in respect the functioning or value of any tokens which may
 * be created stored or transferred using the Work. The Licensor does not warrant that the
 * Work or any use of the Work complies with any law or regulation in any territory where
 * it may be implemented or used or that it will be appropriate for any specific purpose.
 * 
 * Neither the licensor nor any current or former employees, officers, directors, partners,
 * trustees, representatives, agents, advisors, contractors, or volunteers of the Licensor
 * shall be liable for any direct or indirect, special, incidental, consequential or other
 * losses of any kind, in tort, contract or otherwise (including but not limited to loss
 * of revenue, income or profits, or loss of use or data, or loss of reputation, or loss
 * of any economic or other opportunity of whatsoever nature or howsoever arising), arising
 * out of or in connection with (without limitation of any use, misuse, of any ledger system 
 * or use made or its functionality or any performance or operation of any code or protocol
 * caused by bugs or programming or logic errors or otherwise);
 * 
 * A. any offer, purchase, holding, use, sale, exchange or transmission of any
 * cryptographic keys, tokens or assets created, exchanged, stored or arising from any
 * interaction with the Work;
 * 
 * B. any failure in a transmission or loss of any token or assets keys or other digital
 * artefacts due to errors in transmission;
 * 
 * C. bugs, hacks, logic errors or faults in the Work or any communication;
 * 
 * D. system software or apparatus including but not limited to losses caused by errors
 * in holding or transmitting tokens by any third-party;
 * 
 * E. breaches or failure of security including hacker attacks, loss or disclosure of
 * password, loss of private key, unauthorised use or misuse of such passwords or keys;
 * 
 * F. any losses including loss of anticipated savings or other benefits resulting from
 * use of the Work or any changes to the Work (however implemented).
 * 
 * You are solely responsible for; testing, validating and evaluation of all operation
 * logic, functionality, security and appropriateness of using the Work for any commercial
 * or non-commercial purpose and for any reproduction or redistribution by You of the
 * Work. You assume all risks associated with Your use of the Work and the exercise of
 * permissions under this License.
 */
package com.radixdlt.api.store.berkeley;

import com.radixdlt.application.tokens.Amount;
import com.radixdlt.atom.actions.NextRound;
import com.radixdlt.consensus.LedgerHeader;
import com.radixdlt.consensus.LedgerProof;
import com.radixdlt.consensus.TimestampedECDSASignatures;
import com.radixdlt.consensus.bft.Self;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.crypto.HashUtils;
import com.radixdlt.ledger.AccumulatorState;
import com.radixdlt.networks.Addressing;
import com.radixdlt.statecomputer.forks.Forks;
import com.radixdlt.statecomputer.forks.ForksModule;
import com.radixdlt.networks.Network;
import com.radixdlt.statecomputer.forks.MainnetForkConfigsModule;
import com.radixdlt.store.LastStoredProof;
import com.radixdlt.utils.PrivateKeys;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.radixdlt.DefaultSerialization;
import com.radixdlt.SingleNodeAndPeersDeterministicNetworkModule;
import com.radixdlt.api.construction.TxnParser;
import com.radixdlt.api.data.ActionType;
import com.radixdlt.api.store.ClientApiStore.BalanceType;
import com.radixdlt.api.store.TransactionParser;
import com.radixdlt.atom.FixedTokenDefinition;
import com.radixdlt.atom.MutableTokenDefinition;
import com.radixdlt.atom.TxBuilderException;
import com.radixdlt.atom.Txn;
import com.radixdlt.atom.TxnConstructionRequest;
import com.radixdlt.atom.actions.CreateFixedToken;
import com.radixdlt.atom.actions.CreateMutableToken;
import com.radixdlt.constraintmachine.PermissionLevel;
import com.radixdlt.constraintmachine.REProcessedTxn;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.engine.RadixEngine;
import com.radixdlt.engine.RadixEngineException;
import com.radixdlt.engine.parser.REParser;
import com.radixdlt.environment.ScheduledEventDispatcher;
import com.radixdlt.identifiers.AID;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.mempool.MempoolConfig;
import com.radixdlt.qualifier.NumPeers;
import com.radixdlt.serialization.Serialization;
import com.radixdlt.statecomputer.LedgerAndBFTProof;
import com.radixdlt.statecomputer.checkpoint.MockedGenesisModule;
import com.radixdlt.statecomputer.forks.RadixEngineForksLatestOnlyModule;
import com.radixdlt.store.DatabaseEnvironment;
import com.radixdlt.store.DatabaseLocation;
import com.radixdlt.store.berkeley.BerkeleyLedgerEntryStore;
import com.radixdlt.utils.UInt256;
import com.radixdlt.utils.UInt384;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class BerkeleyClientApiStoreTest {
	private static final ECKeyPair VALIDATOR_KEY = PrivateKeys.ofNumeric(1);

	private static final ECKeyPair OWNER_KEYPAIR = ECKeyPair.generateNew();
	private static final REAddr OWNER_ACCOUNT = REAddr.ofPubKeyAccount(OWNER_KEYPAIR.getPublicKey());
	private static final ECKeyPair TOKEN_KEYPAIR = ECKeyPair.generateNew();
	private static final REAddr TOKEN_ACCOUNT = REAddr.ofPubKeyAccount(TOKEN_KEYPAIR.getPublicKey());
	private static final Addressing addressing = Addressing.ofNetwork(Network.LOCALNET);

	private static final String SYMBOL = "cfee";
	private static final REAddr TOKEN = REAddr.ofHashedKey(TOKEN_KEYPAIR.getPublicKey(), SYMBOL);

	private final Serialization serialization = DefaultSerialization.getInstance();
	private final BerkeleyLedgerEntryStore ledgerStore = mock(BerkeleyLedgerEntryStore.class);

	private DatabaseEnvironment environment;

	@Rule
	public final TemporaryFolder folder = new TemporaryFolder();

	@Inject
	private TxnParser txnParser;

	@Inject
	private RadixEngine<LedgerAndBFTProof> engine;

	@Inject
	private REParser parser;

	@Inject
	@Self
	private ECPublicKey self;

	// FIXME: Hack, need this in order to cause provider for genesis to be stored
	@Inject
	@LastStoredProof
	private LedgerProof ledgerProof;

	private Injector createInjector() {
		return Guice.createInjector(
			MempoolConfig.asModule(1000, 0),
			new MainnetForkConfigsModule(),
			new RadixEngineForksLatestOnlyModule(),
			new ForksModule(),
			new SingleNodeAndPeersDeterministicNetworkModule(VALIDATOR_KEY),
			new MockedGenesisModule(
				Set.of(VALIDATOR_KEY.getPublicKey()),
				Amount.ofTokens(1000),
				Amount.ofTokens(100)
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

	@Before
	public void setUp() throws Exception {
		environment = new DatabaseEnvironment(folder.getRoot().getAbsolutePath(), 0);
		var injector = createInjector();
		injector.injectMembers(this);
	}

	@Test
	public void tokenBalancesAreReturned() throws Exception {
		var tokenDef = prepareMutableTokenDef(TOKEN_KEYPAIR.getPublicKey(), SYMBOL);
		var tx = engine.construct(
			TxnConstructionRequest.create()
				.createMutableToken(tokenDef)
				.mint(TOKEN, TOKEN_ACCOUNT, UInt256.EIGHT)
				.burn(TOKEN, TOKEN_ACCOUNT, UInt256.ONE)
				.transfer(TOKEN, TOKEN_ACCOUNT, OWNER_ACCOUNT, UInt256.FOUR)
		).signAndBuild(TOKEN_KEYPAIR::sign);

		var clientApiStore = prepareApiStore(tx);

		clientApiStore.getTokenBalances(TOKEN_ACCOUNT, BalanceType.SPENDABLE)
			.onSuccess(list -> {
				assertEquals(1, list.size());
				assertEquals(UInt384.THREE, list.get(0).getAmount());
				assertTrue(list.get(0).rri().startsWith(SYMBOL));
			})
			.onFailureDo(() -> fail("Failure is not expected here"));

		clientApiStore.getTokenBalances(OWNER_ACCOUNT, BalanceType.SPENDABLE)
			.onSuccess(list -> {
				assertEquals(1, list.size());
				assertEquals(UInt384.FOUR, list.get(0).getAmount());
				assertTrue(list.get(0).rri().startsWith(SYMBOL));
			})
			.onFailureDo(() -> fail("Failure is not expected here"));
	}

	@Test
	public void tokenSupplyIsCalculateProperlyForInitialTokenIssuance() throws Exception {
		var tokenDef = prepareMutableTokenDef(TOKEN_KEYPAIR.getPublicKey(), SYMBOL);
		var addr = REAddr.ofHashedKey(TOKEN_KEYPAIR.getPublicKey(), SYMBOL);
		var rri = addressing.forResources().of(SYMBOL, addr);
		var tx = engine.construct(new CreateMutableToken(tokenDef))
			.signAndBuild(TOKEN_KEYPAIR::sign);

		var clientApiStore = prepareApiStore(tx);

		clientApiStore.getTokenSupply(rri)
			.onSuccess(amount -> assertEquals(UInt384.ZERO, amount))
			.onFailure(this::failWithMessage);
	}

	@Test
	public void tokenSupplyIsCalculateProperlyAfterBurnMint() throws Exception {
		var tokenDef = prepareMutableTokenDef(TOKEN_KEYPAIR.getPublicKey(), SYMBOL);
		var addr = REAddr.ofHashedKey(TOKEN_KEYPAIR.getPublicKey(), SYMBOL);
		var rri = addressing.forResources().of(SYMBOL, addr);
		var tx = engine.construct(
			TxnConstructionRequest.create()
				.createMutableToken(tokenDef)
				.mint(TOKEN, TOKEN_ACCOUNT, UInt256.TEN)
				.burn(TOKEN, TOKEN_ACCOUNT, UInt256.TWO)
		).signAndBuild(TOKEN_KEYPAIR::sign);

		var clientApiStore = prepareApiStore(tx);

		clientApiStore.getTokenSupply(rri)
			.onSuccess(amount -> assertEquals(UInt384.EIGHT, amount))
			.onFailure(this::failWithMessage);
	}

	@Test
	public void mutableTokenDefinitionIsStoredAndAccessible() throws Exception {
		var tokenDef = prepareMutableTokenDef(TOKEN_KEYPAIR.getPublicKey(), SYMBOL);
		var tx = engine.construct(new CreateMutableToken(tokenDef))
			.signAndBuild(TOKEN_KEYPAIR::sign);
		var clientApiStore = prepareApiStore(tx);

		clientApiStore.getTokenDefinition(TOKEN)
			.onSuccess(tokDef -> assertEquals(tokenDef.getName(), tokDef.getName()))
			.onFailure(this::failWithMessage);
	}

	@Test
	public void fixedTokenDefinitionIsStoredAndAccessible() throws Exception {
		var createFixedToken = new CreateFixedToken(
			TOKEN,
			TOKEN_ACCOUNT,
			SYMBOL,
			SYMBOL,
			SYMBOL,
			"",
			"",
			UInt256.ONE
		);
		var tx = engine.construct(createFixedToken).signAndBuild(TOKEN_KEYPAIR::sign);

		var clientApiStore = prepareApiStore(tx);

		clientApiStore.getTokenDefinition(TOKEN)
			.onFailure(this::failWithMessage)
			.onSuccess(tokDef -> assertEquals(SYMBOL, tokDef.getName()));
	}

	@Test
	@Ignore("Something weird going on with this test.")
	public void transactionHistoryIsReturnedInPages() throws Exception {
		var tokenDef = prepareMutableTokenDef(TOKEN_KEYPAIR.getPublicKey(), SYMBOL);
		var tx = engine.construct(
			TxnConstructionRequest.create()
				.createMutableToken(tokenDef)
				.mint(TOKEN, TOKEN_ACCOUNT, UInt256.TEN)
				.transfer(TOKEN, TOKEN_ACCOUNT, OWNER_ACCOUNT, UInt256.FOUR)
				.burn(TOKEN, TOKEN_ACCOUNT, UInt256.ONE)
		).signAndBuild(TOKEN_KEYPAIR::sign);

		var clientApiStore = prepareApiStore(tx);
		var newCursor = new AtomicReference<Instant>();

		clientApiStore.getTransactionHistory(TOKEN_ACCOUNT, 1, Optional.empty())
			.onFailure(this::failWithMessage)
			.onSuccess(list -> {
				assertEquals(1, list.size());

				var entry = list.get(0);

				assertEquals(UInt256.ZERO, entry.getFee());
				assertEquals(4, entry.getActions().size());

				var action = entry.getActions().get(2);

				assertEquals(ActionType.TRANSFER, action.getType());
				assertEquals(UInt256.FOUR, action.getAmount());
				assertEquals(addressing.forAccounts().of(TOKEN_ACCOUNT), action.getFrom());
				assertEquals(addressing.forAccounts().of(REAddr.ofPubKeyAccount(OWNER_KEYPAIR.getPublicKey())), action.getTo());

				newCursor.set(entry.timestamp());
			});

		assertNotNull(newCursor.get());

		clientApiStore.getTransactionHistory(TOKEN_ACCOUNT, 1, Optional.of(newCursor.get()))
			.onFailure(this::failWithMessage)
			.onSuccess(list -> assertEquals(0, list.size()));
	}

	@Test
	public void singleTransactionIsLocatedAndReturned() throws Exception {
		var tokenDef = prepareMutableTokenDef(TOKEN_KEYPAIR.getPublicKey(), SYMBOL);
		var tx = engine.construct(
			TxnConstructionRequest.create()
				.createMutableToken(tokenDef)
				.mint(TOKEN, TOKEN_ACCOUNT, UInt256.TEN)
				.transfer(TOKEN, TOKEN_ACCOUNT, OWNER_ACCOUNT, UInt256.FOUR)
				.burn(TOKEN, TOKEN_ACCOUNT, UInt256.ONE)
		).signAndBuild(TOKEN_KEYPAIR::sign);

		var txMap = new HashMap<AID, Txn>();
		var clientApiStore = prepareApiStore(tx, txMap);

		clientApiStore.getTransaction(tx.getId())
			.onFailure(this::failWithMessage)
			.onSuccess(entry -> assertEquals(tx.getId(), entry.getTxId()));
	}

	@Test
	public void incorrectPageSizeIsRejected() throws TxBuilderException, RadixEngineException {
		var tokenDef = prepareMutableTokenDef(TOKEN_KEYPAIR.getPublicKey(), SYMBOL);
		var tx = engine.construct(
			TxnConstructionRequest.create()
				.createMutableToken(tokenDef)
				.mint(TOKEN, TOKEN_ACCOUNT, UInt256.TEN)
				.transfer(TOKEN, TOKEN_ACCOUNT, OWNER_ACCOUNT, UInt256.FOUR)
				.burn(TOKEN, TOKEN_ACCOUNT, UInt256.ONE)
		).signAndBuild(TOKEN_KEYPAIR::sign);

		var clientApiStore = prepareApiStore(tx);
		clientApiStore.getTransactionHistory(TOKEN_ACCOUNT, 0, Optional.empty())
			.onSuccess(list -> fail("Request must be rejected"));
	}

	private BerkeleyClientApiStore prepareApiStore(Txn tx) throws TxBuilderException, RadixEngineException {
		return prepareApiStore(tx, new HashMap<>());
	}

	@SuppressWarnings("unchecked")
	private BerkeleyClientApiStore prepareApiStore(Txn tx, Map<AID, Txn> txMap) throws TxBuilderException, RadixEngineException {
		var ledgerProof = new LedgerProof(
			HashUtils.random256(),
			LedgerHeader.create(0, View.of(9), new AccumulatorState(3, HashUtils.zero256()), 0),
			new TimestampedECDSASignatures()
		);
		var tx1 = engine.construct(new NextRound(1, true, 2, i -> self))
			.buildWithoutSignature();
		var transactions = engine.execute(List.of(tx1, tx), LedgerAndBFTProof.create(ledgerProof), PermissionLevel.SUPER_USER)
			.getProcessedTxns()
			.stream()
			.map(REProcessedTxn::getTxn)
			.collect(Collectors.toList());

		transactions.forEach(txn -> txMap.put(txn.getId(), txn));

		when(ledgerStore.get(any(AID.class)))
			.thenAnswer(invocation -> Optional.ofNullable(txMap.get(invocation.getArgument(0, AID.class))));

		//Insert necessary values on DB rebuild
		doAnswer(invocation -> {
			transactions.forEach(invocation.<Consumer<Txn>>getArgument(0));
			return null;
		}).when(ledgerStore).forEach(any(Consumer.class));

		return new BerkeleyClientApiStore(
			environment,
			parser,
			txnParser,
			ledgerStore,
			serialization,
			mock(SystemCounters.class),
			mock(ScheduledEventDispatcher.class),
			new TransactionParser(addressing),
			true,
			addressing,
			mock(Forks.class)
		);
	}

	private void failWithMessage(com.radixdlt.utils.functional.Failure failure) {
		fail(failure.message());
	}

	private MutableTokenDefinition prepareMutableTokenDef(ECPublicKey key, String symbol) {
		return new MutableTokenDefinition(
			key, symbol, symbol, symbol, null, null
		);
	}

	private FixedTokenDefinition prepareFixedTokenDef() {
		var symbol = SYMBOL;

		return new FixedTokenDefinition(
			symbol, symbol, symbol, null, null, UInt256.ONE
		);
	}
}
