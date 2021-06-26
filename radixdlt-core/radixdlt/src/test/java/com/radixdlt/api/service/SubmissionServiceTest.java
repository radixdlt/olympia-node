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
package com.radixdlt.api.service;

import com.radixdlt.atommodel.tokens.Amount;
import com.radixdlt.consensus.bft.BFTValidator;
import com.radixdlt.consensus.bft.BFTValidatorSet;
import com.radixdlt.consensus.liveness.ProposerElection;
import com.radixdlt.consensus.liveness.WeightedRotatingLeaders;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.ledger.LedgerUpdate;
import com.radixdlt.statecomputer.forks.ForksModule;
import com.radixdlt.statecomputer.forks.RERulesConfig;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.radix.TokenIssuance;

import com.google.common.collect.ImmutableList;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Module;
import com.google.inject.TypeLiteral;
import com.google.inject.multibindings.ProvidesIntoSet;
import com.radixdlt.DefaultSerialization;
import com.radixdlt.api.data.PreparedTransaction;
import com.radixdlt.api.data.action.TransactionAction;
import com.radixdlt.api.store.ClientApiStore;
import com.radixdlt.atom.TxBuilderException;
import com.radixdlt.atom.TxLowLevelBuilder;
import com.radixdlt.atom.Txn;
import com.radixdlt.atom.actions.TransferToken;
import com.radixdlt.consensus.LedgerProof;
import com.radixdlt.consensus.Sha256Hasher;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.PersistentVertexStore;
import com.radixdlt.consensus.bft.Self;
import com.radixdlt.constraintmachine.PermissionLevel;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.counters.SystemCountersImpl;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.Hasher;
import com.radixdlt.engine.RadixEngine;
import com.radixdlt.engine.RadixEngineException;
import com.radixdlt.environment.EventDispatcher;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.ledger.AccumulatorState;
import com.radixdlt.ledger.LedgerAccumulator;
import com.radixdlt.ledger.SimpleLedgerAccumulatorAndVerifier;
import com.radixdlt.ledger.VerifiedTxnsAndProof;
import com.radixdlt.mempool.MempoolAdd;
import com.radixdlt.mempool.MempoolAddFailure;
import com.radixdlt.mempool.MempoolAddSuccess;
import com.radixdlt.mempool.MempoolConfig;
import com.radixdlt.mempool.MempoolRelayTrigger;
import com.radixdlt.serialization.Serialization;
import com.radixdlt.statecomputer.AtomsRemovedFromMempool;
import com.radixdlt.statecomputer.InvalidProposedTxn;
import com.radixdlt.statecomputer.LedgerAndBFTProof;
import com.radixdlt.statecomputer.RadixEngineConfig;
import com.radixdlt.statecomputer.RadixEngineModule;
import com.radixdlt.statecomputer.RadixEngineStateComputer;
import com.radixdlt.statecomputer.StakedValidators;
import com.radixdlt.statecomputer.TxnsCommittedToLedger;
import com.radixdlt.statecomputer.checkpoint.Genesis;
import com.radixdlt.statecomputer.checkpoint.MockedGenesisModule;
import com.radixdlt.statecomputer.checkpoint.RadixEngineCheckpointModule;
import com.radixdlt.statecomputer.forks.RadixEngineForksLatestOnlyModule;
import com.radixdlt.store.EngineStore;
import com.radixdlt.store.InMemoryEngineStore;
import com.radixdlt.sync.CommittedReader;
import com.radixdlt.utils.TypedMocks;
import com.radixdlt.utils.UInt256;
import com.radixdlt.utils.functional.Result;

import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

public class SubmissionServiceTest {
	@Inject
	@Genesis
	private VerifiedTxnsAndProof genesisTxns;

	private ECKeyPair key = ECKeyPair.generateNew();

	@Inject
	private RadixEngine<LedgerAndBFTProof> radixEngine;

	@Inject
	private RadixEngineStateComputer sut;

	@Inject
	private SubmissionService submissionService;

	private REAddr nativeToken = REAddr.ofNativeToken();

	private final InMemoryEngineStore<LedgerAndBFTProof> engineStore = new InMemoryEngineStore<>();
	private final Serialization serialization = DefaultSerialization.getInstance();
	private final ImmutableList<ECKeyPair> registeredNodes = ImmutableList.of(
		ECKeyPair.generateNew(),
		ECKeyPair.generateNew()
	);

	private static final ECKeyPair ALICE_KEYPAIR = ECKeyPair.generateNew();
	private static final REAddr ALICE_ACCT = REAddr.ofPubKeyAccount(ALICE_KEYPAIR.getPublicKey());
	private static final ECKeyPair BOB_KEYPAIR = ECKeyPair.generateNew();
	private static final REAddr BOB_ACCT = REAddr.ofPubKeyAccount(BOB_KEYPAIR.getPublicKey());

	private static final BFTNode NODE = BFTNode.create(ECKeyPair.generateNew().getPublicKey());
	private static final Hasher hasher = Sha256Hasher.withDefaultSerialization();
	private static final UInt256 BIG_AMOUNT = UInt256.TEN.pow(20);

	private EventDispatcher<MempoolAdd> mempoolAddEventDispatcher() {
		return add -> add.onSuccess(MempoolAddSuccess.create(add.getTxns().get(0)));
	}

	private Module localModule() {
		return new AbstractModule() {

			@Override
			public void configure() {
				install(new RadixEngineForksLatestOnlyModule(new RERulesConfig(false, 10, 2, Amount.ofTokens(10))));
				install(new ForksModule());
				install(RadixEngineConfig.asModule(1, 100, 50));
				install(MempoolConfig.asModule(10, 10));

				bind(new TypeLiteral<ImmutableList<ECPublicKey>>() { }).annotatedWith(Genesis.class)
					.toInstance(registeredNodes.stream().map(ECKeyPair::getPublicKey).collect(ImmutableList.toImmutableList()));
				var validatorSet = BFTValidatorSet.from(registeredNodes.stream().map(ECKeyPair::getPublicKey)
					.map(BFTNode::create)
					.map(n -> BFTValidator.from(n, UInt256.ONE)));
				bind(ProposerElection.class).toInstance(new WeightedRotatingLeaders(validatorSet));
				bind(Serialization.class).toInstance(serialization);
				bind(Hasher.class).toInstance(Sha256Hasher.withDefaultSerialization());
				bind(new TypeLiteral<EngineStore<LedgerAndBFTProof>>() { }).toInstance(engineStore);
				bind(PersistentVertexStore.class).toInstance(mock(PersistentVertexStore.class));
				bind(CommittedReader.class).toInstance(CommittedReader.mocked());
				bind(LedgerAccumulator.class).to(SimpleLedgerAccumulatorAndVerifier.class);
				bind(new TypeLiteral<EventDispatcher<MempoolAddSuccess>>() { })
					.toInstance(TypedMocks.rmock(EventDispatcher.class));
				bind(new TypeLiteral<EventDispatcher<MempoolAddFailure>>() { })
					.toInstance(TypedMocks.rmock(EventDispatcher.class));
				bind(new TypeLiteral<EventDispatcher<InvalidProposedTxn>>() { })
					.toInstance(TypedMocks.rmock(EventDispatcher.class));
				bind(new TypeLiteral<EventDispatcher<AtomsRemovedFromMempool>>() { })
					.toInstance(TypedMocks.rmock(EventDispatcher.class));
				bind(new TypeLiteral<EventDispatcher<TxnsCommittedToLedger>>() { })
					.toInstance(TypedMocks.rmock(EventDispatcher.class));
				bind(new TypeLiteral<EventDispatcher<MempoolRelayTrigger>>() { })
					.toInstance(TypedMocks.rmock(EventDispatcher.class));
				bind(new TypeLiteral<EventDispatcher<MempoolAdd>>() { })
					.toInstance(mempoolAddEventDispatcher());
				bind(new TypeLiteral<EventDispatcher<LedgerUpdate>>() { })
					.toInstance(TypedMocks.rmock(EventDispatcher.class));

				bind(BFTNode.class).annotatedWith(Self.class).toInstance(NODE);

				bind(SystemCounters.class).to(SystemCountersImpl.class);

				bind(ClientApiStore.class).toInstance(mock(ClientApiStore.class));
			}

			@ProvidesIntoSet
			TokenIssuance tokenIssuance() {
				return TokenIssuance.of(key.getPublicKey(), BIG_AMOUNT.multiply(BIG_AMOUNT));
			}
		};
	}

	private void setupGenesis() throws RadixEngineException {
		var branch = radixEngine.transientBranch();
		branch.execute(genesisTxns.getTxns(), PermissionLevel.SYSTEM);
		final var genesisValidatorSet = branch.getComputedState(StakedValidators.class).toValidatorSet();
		radixEngine.deleteBranches();

		var genesisLedgerHeader = LedgerProof.genesis(
			new AccumulatorState(0, hasher.hash(genesisTxns.getTxns().get(0).getId())),
			genesisValidatorSet,
			0
		);

		if (!genesisLedgerHeader.isEndOfEpoch()) {
			throw new IllegalStateException("Genesis must be end of epoch");
		}

		radixEngine.execute(genesisTxns.getTxns(), LedgerAndBFTProof.create(genesisLedgerHeader), PermissionLevel.SYSTEM);
	}

	@Before
	public void setup() throws Exception {
		var injector = Guice.createInjector(
			new RadixEngineCheckpointModule(),
			new RadixEngineModule(),
			new MockedGenesisModule(),
			localModule()
		);
		injector.injectMembers(this);
		setupGenesis();
	}

	@Test
	public void testPrepareTransaction() throws Exception {
		var acct = REAddr.ofPubKeyAccount(key.getPublicKey());
		var action = new TransferToken(nativeToken, acct, ALICE_ACCT, BIG_AMOUNT);

		var tx = radixEngine.construct(action).signAndBuild(key::sign);

		radixEngine.execute(List.of(tx));

		var steps = List.of(
			TransactionAction.transfer(
				ALICE_ACCT,
				BOB_ACCT,
				UInt256.FOUR,
				nativeToken
			)
		);

		var result = submissionService.prepareTransaction(acct, steps, Optional.of("message"), false);

		result
			.onFailureDo(Assert::fail)
			.onSuccess(prepared -> {
				var json = prepared.asJson();

				assertTrue(json.has("fee"));
				assertEquals("100000000000000000", json.get("fee"));

				assertTrue(json.has("transaction"));

				var transaction = json.getJSONObject("transaction");
				assertTrue(transaction.has("blob"));
				assertTrue(transaction.has("hashOfBlobToSign"));
			});
	}

	@Test
	public void testCalculateTxId() throws Exception {
		var result = buildTransaction();
		var signature = result.map(prepared -> ALICE_KEYPAIR.sign(prepared.getHashToSign()));

		result
			.onFailureDo(Assert::fail)
			.flatMap(prep ->
						 signature.flatMap(sig ->
											   submissionService.calculateTxId(prep.getBlob(), sig)))
			.onFailureDo(Assert::fail)
			.onSuccess(Assert::assertNotNull);
	}

	@Test
	public void testSubmitTx() throws Exception {
		var result = buildTransaction();
		var signature = result.map(prepared -> ALICE_KEYPAIR.sign(prepared.getHashToSign()));

		result
			.onFailureDo(Assert::fail)
			.flatMap(prepared ->
						 signature.flatMap(recoverable ->
							 Result.ok(TxLowLevelBuilder.newBuilder(prepared.getBlob()).sig(recoverable).build())
							 .map(Txn::getId)
							 .flatMap(txId -> submissionService.submitTx(prepared.getBlob(), recoverable, txId))))
			.onFailureDo(Assert::fail)
			.onSuccess(Assert::assertNotNull);
	}

	private Result<PreparedTransaction> buildTransaction() throws TxBuilderException, RadixEngineException {
		var acct = REAddr.ofPubKeyAccount(key.getPublicKey());
		var action = new TransferToken(nativeToken, acct, ALICE_ACCT, BIG_AMOUNT);

		var tx = radixEngine.construct(action).signAndBuild(key::sign);

		radixEngine.execute(List.of(tx));

		var steps = List.of(
			TransactionAction.transfer(ALICE_ACCT, BOB_ACCT, UInt256.FOUR, nativeToken)
		);

		return submissionService.prepareTransaction(acct, steps, Optional.of("message"), false);
	}
}