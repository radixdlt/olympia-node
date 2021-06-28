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

package com.radixdlt.statecomputer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.common.hash.HashCode;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Module;
import com.google.inject.TypeLiteral;
import com.radixdlt.DefaultSerialization;
import com.radixdlt.atom.TxBuilder;
import com.radixdlt.atom.TxBuilderException;
import com.radixdlt.atom.SubstateId;
import com.radixdlt.atom.TxLowLevelBuilder;
import com.radixdlt.atom.Txn;
import com.radixdlt.atom.TxnConstructionRequest;
import com.radixdlt.atom.actions.RegisterValidator;
import com.radixdlt.atom.actions.SystemNextEpoch;
import com.radixdlt.atom.actions.SystemNextView;
import com.radixdlt.atommodel.system.state.RoundData;
import com.radixdlt.consensus.BFTHeader;
import com.radixdlt.consensus.LedgerHeader;
import com.radixdlt.consensus.QuorumCertificate;
import com.radixdlt.consensus.TimestampedECDSASignatures;
import com.radixdlt.consensus.LedgerProof;
import com.radixdlt.consensus.Sha256Hasher;
import com.radixdlt.consensus.UnverifiedVertex;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.BFTValidator;
import com.radixdlt.consensus.bft.BFTValidatorSet;
import com.radixdlt.consensus.bft.PersistentVertexStore;
import com.radixdlt.consensus.bft.VerifiedVertex;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.consensus.liveness.ProposerElection;
import com.radixdlt.consensus.liveness.WeightedRotatingLeaders;
import com.radixdlt.constraintmachine.CMErrorCode;
import com.radixdlt.constraintmachine.ConstraintMachineException;
import com.radixdlt.constraintmachine.PermissionLevel;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.counters.SystemCountersImpl;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.HashUtils;
import com.radixdlt.crypto.Hasher;
import com.radixdlt.engine.MetadataException;
import com.radixdlt.engine.RadixEngine;
import com.radixdlt.engine.RadixEngineException;
import com.radixdlt.environment.EventDispatcher;
import com.radixdlt.ledger.AccumulatorState;
import com.radixdlt.ledger.ByzantineQuorumException;
import com.radixdlt.ledger.LedgerAccumulator;
import com.radixdlt.ledger.LedgerUpdate;
import com.radixdlt.ledger.SimpleLedgerAccumulatorAndVerifier;
import com.radixdlt.ledger.StateComputerLedger.StateComputerResult;
import com.radixdlt.ledger.VerifiedTxnsAndProof;
import com.radixdlt.mempool.MempoolAddFailure;
import com.radixdlt.mempool.MempoolAddSuccess;
import com.radixdlt.mempool.MempoolConfig;
import com.radixdlt.mempool.MempoolRelayTrigger;
import com.radixdlt.serialization.Serialization;
import com.radixdlt.statecomputer.checkpoint.Genesis;
import com.radixdlt.statecomputer.checkpoint.MockedGenesisModule;
import com.radixdlt.statecomputer.checkpoint.RadixEngineCheckpointModule;
import com.radixdlt.statecomputer.forks.ForksModule;
import com.radixdlt.statecomputer.forks.RERules;
import com.radixdlt.statecomputer.forks.RERulesConfig;
import com.radixdlt.statecomputer.forks.RadixEngineForksLatestOnlyModule;
import com.radixdlt.store.EngineStore;
import com.radixdlt.store.InMemoryEngineStore;
import com.radixdlt.sync.CommittedReader;
import com.radixdlt.utils.TypedMocks;
import com.radixdlt.utils.UInt256;

import java.util.List;
import java.util.stream.Stream;
import org.assertj.core.api.Condition;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class RadixEngineStateComputerTest {
	@Rule
	public TemporaryFolder folder = new TemporaryFolder();

	@Inject
	@Genesis
	private VerifiedTxnsAndProof genesisTxns;

	@Inject
	private RadixEngine<LedgerAndBFTProof> radixEngine;

	@Inject
	private RadixEngineStateComputer sut;

	@Inject
	private RERules rules;

	@Inject
	private ProposerElection proposerElection;

	private Serialization serialization = DefaultSerialization.getInstance();
	private InMemoryEngineStore<LedgerAndBFTProof> engineStore;
	private ImmutableList<ECKeyPair> registeredNodes = ImmutableList.of(
		ECKeyPair.generateNew(),
		ECKeyPair.generateNew()
	);
	private ECKeyPair unregisteredNode = ECKeyPair.generateNew();

	private static final Hasher hasher = Sha256Hasher.withDefaultSerialization();

	private Module getExternalModule() {
		return new AbstractModule() {

			@Override
			public void configure() {
				bind(new TypeLiteral<ImmutableList<ECKeyPair>>() { }).annotatedWith(Genesis.class)
					.toInstance(registeredNodes);
				var validatorSet = BFTValidatorSet.from(registeredNodes.stream().map(ECKeyPair::getPublicKey)
					.map(BFTNode::create)
					.map(n -> BFTValidator.from(n, UInt256.ONE)));

				bind(ProposerElection.class).toInstance(new WeightedRotatingLeaders(validatorSet));
				bind(Serialization.class).toInstance(serialization);
				bind(Hasher.class).toInstance(Sha256Hasher.withDefaultSerialization());
				bind(new TypeLiteral<EngineStore<LedgerAndBFTProof>>() { }).toInstance(engineStore);
				bind(PersistentVertexStore.class).toInstance(mock(PersistentVertexStore.class));

				install(MempoolConfig.asModule(10, 10));
				install(new ForksModule());
				install(new RadixEngineForksLatestOnlyModule(new RERulesConfig(false, 10, 2)));
				install(RadixEngineConfig.asModule(1, 100, 50));

				// HACK
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
				bind(new TypeLiteral<EventDispatcher<LedgerUpdate>>() { })
					.toInstance(TypedMocks.rmock(EventDispatcher.class));

				bind(SystemCounters.class).to(SystemCountersImpl.class);
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
	public void setup() throws RadixEngineException {
		this.engineStore = new InMemoryEngineStore<>();
		Guice.createInjector(
			new RadixEngineCheckpointModule(),
			new RadixEngineStateComputerModule(),
			new RadixEngineModule(),
			new MockedGenesisModule(),
			getExternalModule()
		).injectMembers(this);
		setupGenesis();
	}

	private Txn systemUpdateTxn(long nextView, long nextEpoch) throws TxBuilderException {
		TxBuilder builder;
		if (nextEpoch >= 2) {
			var request = TxnConstructionRequest.create()
				.action(new SystemNextView(10, true, 0,  v -> proposerElection.getProposer(View.of(v)).getKey()))
				.action(new SystemNextEpoch(u -> List.of(registeredNodes.get(0).getPublicKey()), 0));
			builder = radixEngine.construct(request);
		} else {
			builder = radixEngine.construct(new SystemNextView(
				nextView,
				false,
				0,
				i -> registeredNodes.get(0).getPublicKey()
			));
		}

		return builder.buildWithoutSignature();
	}

	private Txn systemUpdateCommand(long nextView, long nextEpoch) throws TxBuilderException {
		return systemUpdateTxn(nextView, nextEpoch);
	}

	private Txn registerCommand(ECKeyPair keyPair) throws TxBuilderException {
		return radixEngine.construct(new RegisterValidator(keyPair.getPublicKey()))
			.signAndBuild(keyPair::sign);
	}

	@Test
	@Ignore("Ignore for now given need for more refactoring to get this test to work")
	public void executing_non_epoch_high_view_should_return_no_validator_set() {
		// Arrange
		var v = UnverifiedVertex.create(mock(QuorumCertificate.class), View.of(9), List.of(), BFTNode.random());
		var vertex = new VerifiedVertex(v, mock(HashCode.class));

		// Action
		var result = sut.prepare(List.of(), vertex, 0);

		// Assert
		assertThat(result.getSuccessfulCommands()).hasSize(1);
		assertThat(result.getFailedCommands()).isEmpty();
		assertThat(result.getNextValidatorSet()).isEmpty();
	}

	@Test
	public void executing_epoch_high_view_should_return_next_validator_set() {
		// Arrange
		var qc = mock(QuorumCertificate.class);
		var parentHeader = mock(BFTHeader.class);
		when(parentHeader.getView()).thenReturn(View.of(0));
		when(qc.getProposed()).thenReturn(parentHeader);
		var unverified = UnverifiedVertex.create(qc, View.of(11), List.of(), BFTNode.random());
		var vertex = new VerifiedVertex(unverified, mock(HashCode.class));

		// Act
		StateComputerResult result = sut.prepare(List.of(), vertex, 0);

		// Assert
		assertThat(result.getSuccessfulCommands()).hasSize(1);
		assertThat(result.getFailedCommands()).isEmpty();
		assertThat(result.getNextValidatorSet()).hasValueSatisfying(set ->
			assertThat(set.getValidators())
				.isNotEmpty()
				.allMatch(v -> v.getNode().getKey().equals(unregisteredNode.getPublicKey())
					|| registeredNodes.stream().anyMatch(k -> k.getPublicKey().equals(v.getNode().getKey())))
		);
	}

	@Test
	public void executing_epoch_high_view_with_register_should_not_return_new_next_validator_set() throws Exception {
		// Arrange
		ECKeyPair keyPair = ECKeyPair.generateNew();
		var txn = registerCommand(keyPair);
		BFTNode node = BFTNode.create(keyPair.getPublicKey());
		var qc = mock(QuorumCertificate.class);
		var parentHeader = mock(BFTHeader.class);
		when(parentHeader.getView()).thenReturn(View.of(0));
		when(qc.getProposed()).thenReturn(parentHeader);
		var v = UnverifiedVertex.create(qc, View.of(11), List.of(txn), BFTNode.random());
		var vertex = new VerifiedVertex(v, mock(HashCode.class));

		// Act
		StateComputerResult result = sut.prepare(List.of(), vertex, 0);

		// Assert
		assertThat(result.getSuccessfulCommands()).hasSize(1); // since high view, command is not executed
		assertThat(result.getNextValidatorSet()).hasValueSatisfying(s -> {
			assertThat(s.getValidators()).hasSize(2);
			assertThat(s.getValidators()).extracting(BFTValidator::getNode).doesNotContain(node);
		});
	}

	@Test
	public void preparing_system_update_from_vertex_should_fail() throws TxBuilderException {
		// Arrange
		var txn = radixEngine.construct(new SystemNextView(1, false, 0, i -> proposerElection.getProposer(View.of(i)).getKey()))
			.buildWithoutSignature();
		var illegalTxn = TxLowLevelBuilder.newBuilder(rules.getSerialization())
			.down(SubstateId.ofSubstate(txn.getId(), 3))
			.up(new RoundData(2, 0))
			.end()
			.build();
		var v = UnverifiedVertex.create(
			mock(QuorumCertificate.class),
			View.of(1),
			List.of(illegalTxn),
			proposerElection.getProposer(View.of(1))
		);
		var vertex = new VerifiedVertex(v, mock(HashCode.class));

		// Act
		var result = sut.prepare(ImmutableList.of(), vertex, 0);

		// Assert
		assertThat(result.getSuccessfulCommands()).hasSize(1);
		assertThat(result.getFailedCommands()).hasValueSatisfying(
			new Condition<>(
				e -> {
					RadixEngineException ex = (RadixEngineException) e;
					ConstraintMachineException cmException = (ConstraintMachineException) ex.getCause();
					return cmException.getErrorCode().equals(CMErrorCode.PERMISSION_LEVEL_ERROR);
				},
				"Is invalid_execution_permission error"
			)

		);
	}

	// TODO: should catch this and log it somewhere as proof of byzantine quorum
	@Test
	// Note that checking upper bound view for epoch now requires additional
	// state not easily obtained where checked in the RadixEngine
	@Ignore("FIXME: Reinstate when upper bound on epoch view is in place.")
	public void committing_epoch_high_views_should_fail() throws TxBuilderException {
		// Arrange
		var cmd0 = systemUpdateCommand(10, 1);
		var ledgerProof = new LedgerProof(
			HashUtils.random256(),
			LedgerHeader.create(0, View.of(11), new AccumulatorState(3, HashUtils.zero256()), 0),
			new TimestampedECDSASignatures()
		);
		var commandsAndProof = VerifiedTxnsAndProof.create(
			ImmutableList.of(cmd0),
			ledgerProof
		);

		// Act
		// Assert
		assertThatThrownBy(() -> sut.commit(commandsAndProof, null))
			.isInstanceOf(ByzantineQuorumException.class);
	}

	// TODO: should catch this and log it somewhere as proof of byzantine quorum
	@Test
	public void committing_epoch_change_with_additional_cmds_should_fail() throws Exception {
		// Arrange
		ECKeyPair keyPair = ECKeyPair.generateNew();
		var cmd0 = systemUpdateCommand(0, 2);
		var cmd1 = registerCommand(keyPair);
		var ledgerProof = new LedgerProof(
			HashUtils.random256(),
			LedgerHeader.create(0, View.of(9), new AccumulatorState(3, HashUtils.zero256()), 0),
			new TimestampedECDSASignatures()
		);
		var commandsAndProof = VerifiedTxnsAndProof.create(
			ImmutableList.of(cmd0, cmd1),
			ledgerProof
		);

		// Act
		// Assert
		assertThatThrownBy(() -> sut.commit(commandsAndProof, null))
			.isInstanceOf(ByzantineQuorumException.class);
	}

	// TODO: should catch this and log it somewhere as proof of byzantine quorum
	@Test
	public void committing_epoch_change_with_different_validator_signed_should_fail() throws Exception {
		// Arrange
		var cmd1 = systemUpdateCommand(0, 2);
		var ledgerProof = new LedgerProof(
			HashUtils.random256(),
			LedgerHeader.create(0, View.of(9), new AccumulatorState(3, HashUtils.zero256()), 0,
				BFTValidatorSet.from(Stream.of(BFTValidator.from(BFTNode.random(), UInt256.ONE)))
			),
			new TimestampedECDSASignatures()
		);
		var commandsAndProof = VerifiedTxnsAndProof.create(
			ImmutableList.of(cmd1),
			ledgerProof
		);

		// Act
		// Assert
		assertThatThrownBy(() -> sut.commit(commandsAndProof, null))
			.isInstanceOf(MetadataException.class);
	}

	// TODO: should catch this and log it somewhere as proof of byzantine quorum
	@Test
	public void committing_epoch_change_when_there_shouldnt_be_one__should_fail() throws TxBuilderException {
		// Arrange
		var cmd0 = systemUpdateCommand(1, 1);
		var ledgerProof = new LedgerProof(
			HashUtils.random256(),
			LedgerHeader.create(0, View.of(9), new AccumulatorState(3, HashUtils.zero256()), 0,
				BFTValidatorSet.from(Stream.of(BFTValidator.from(BFTNode.random(), UInt256.ONE)))
			),
			new TimestampedECDSASignatures()
		);
		var commandsAndProof = VerifiedTxnsAndProof.create(
			ImmutableList.of(cmd0),
			ledgerProof
		);

		// Act
		// Assert
		assertThatThrownBy(() -> sut.commit(commandsAndProof, null))
			.isInstanceOf(MetadataException.class);
	}
}
