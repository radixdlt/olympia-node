/*
 * (C) Copyright 2020 Radix DLT Ltd
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
 */

package com.radixdlt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.google.common.collect.ImmutableList;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Names;
import com.radixdlt.atom.TxBuilder;
import com.radixdlt.atom.TxBuilderException;
import com.radixdlt.atom.SubstateId;
import com.radixdlt.atom.TxLowLevelBuilder;
import com.radixdlt.atom.Txn;
import com.radixdlt.atommodel.system.SystemParticle;
import com.radixdlt.consensus.BFTHeader;
import com.radixdlt.consensus.LedgerHeader;
import com.radixdlt.consensus.TimestampedECDSASignatures;
import com.radixdlt.consensus.LedgerProof;
import com.radixdlt.consensus.Sha256Hasher;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.BFTValidator;
import com.radixdlt.consensus.bft.BFTValidatorSet;
import com.radixdlt.consensus.bft.PersistentVertexStore;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.constraintmachine.CMErrorCode;
import com.radixdlt.constraintmachine.PermissionLevel;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.counters.SystemCountersImpl;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.HashUtils;
import com.radixdlt.crypto.Hasher;
import com.radixdlt.engine.RadixEngine;
import com.radixdlt.engine.RadixEngineException;
import com.radixdlt.environment.EventDispatcher;
import com.radixdlt.identifiers.RadixAddress;
import com.radixdlt.ledger.AccumulatorState;
import com.radixdlt.ledger.ByzantineQuorumException;
import com.radixdlt.ledger.StateComputerLedger.StateComputerResult;
import com.radixdlt.ledger.VerifiedTxnsAndProof;
import com.radixdlt.mempool.MempoolAddFailure;
import com.radixdlt.mempool.MempoolAddSuccess;
import com.radixdlt.mempool.MempoolMaxSize;
import com.radixdlt.mempool.MempoolThrottleMs;
import com.radixdlt.serialization.Serialization;
import com.radixdlt.statecomputer.AtomsCommittedToLedger;
import com.radixdlt.statecomputer.EpochCeilingView;
import com.radixdlt.statecomputer.InvalidProposedCommand;
import com.radixdlt.statecomputer.MaxValidators;
import com.radixdlt.statecomputer.AtomsRemovedFromMempool;
import com.radixdlt.statecomputer.MinValidators;
import com.radixdlt.statecomputer.LedgerAndBFTProof;
import com.radixdlt.statecomputer.RadixEngineModule;
import com.radixdlt.statecomputer.RadixEngineStateComputer;

import com.radixdlt.statecomputer.RegisteredValidators;
import com.radixdlt.statecomputer.Stakes;
import com.radixdlt.statecomputer.ValidatorSetBuilder;
import com.radixdlt.statecomputer.checkpoint.Genesis;
import com.radixdlt.statecomputer.checkpoint.MockedGenesisAtomModule;
import com.radixdlt.statecomputer.checkpoint.RadixEngineCheckpointModule;
import com.radixdlt.statecomputer.transaction.EmptyTransactionCheckModule;
import com.radixdlt.store.EngineStore;
import com.radixdlt.store.InMemoryEngineStore;
import com.radixdlt.utils.TypedMocks;
import com.radixdlt.utils.UInt256;

import java.util.List;
import java.util.stream.Stream;
import org.assertj.core.api.Condition;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

public class RadixEngineStateComputerTest {
	@Inject
	@Genesis
	private List<Txn> genesisTxns;

	@Inject
	private RadixEngine<LedgerAndBFTProof> radixEngine;

	@Inject
	private RadixEngineStateComputer sut;

	@Inject
	private ValidatorSetBuilder validatorSetBuilder;


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
				bind(ECKeyPair.class).annotatedWith(Names.named("universeKey")).toInstance(ECKeyPair.generateNew());
				bind(new TypeLiteral<ImmutableList<ECKeyPair>>() { }).annotatedWith(Genesis.class)
					.toInstance(registeredNodes);
				bind(Serialization.class).toInstance(serialization);
				bind(Hasher.class).toInstance(Sha256Hasher.withDefaultSerialization());
				bind(new TypeLiteral<EngineStore<LedgerAndBFTProof>>() { }).toInstance(engineStore);
				bind(PersistentVertexStore.class).toInstance(mock(PersistentVertexStore.class));
				bindConstant().annotatedWith(Names.named("magic")).to(0);
				bindConstant().annotatedWith(MinValidators.class).to(1);
				bindConstant().annotatedWith(MaxValidators.class).to(100);
				bindConstant().annotatedWith(MempoolMaxSize.class).to(10);
				bindConstant().annotatedWith(MempoolThrottleMs.class).to(10L);
				bind(View.class).annotatedWith(EpochCeilingView.class).toInstance(View.of(10));

				bind(new TypeLiteral<EventDispatcher<MempoolAddSuccess>>() { })
						.toInstance(TypedMocks.rmock(EventDispatcher.class));
				bind(new TypeLiteral<EventDispatcher<MempoolAddFailure>>() { })
					.toInstance(TypedMocks.rmock(EventDispatcher.class));
				bind(new TypeLiteral<EventDispatcher<InvalidProposedCommand>>() { })
						.toInstance(TypedMocks.rmock(EventDispatcher.class));
				bind(new TypeLiteral<EventDispatcher<AtomsRemovedFromMempool>>() { })
						.toInstance(TypedMocks.rmock(EventDispatcher.class));
				bind(new TypeLiteral<EventDispatcher<AtomsCommittedToLedger>>() { })
					.toInstance(TypedMocks.rmock(EventDispatcher.class));

				bind(SystemCounters.class).to(SystemCountersImpl.class);
			}
		};
	}

	private void setupGenesis() throws RadixEngineException {
		var branch = radixEngine.transientBranch();
		branch.execute(genesisTxns, PermissionLevel.SYSTEM);
		final var genesisValidatorSet = validatorSetBuilder.buildValidatorSet(
			branch.getComputedState(RegisteredValidators.class),
			branch.getComputedState(Stakes.class)
		);
		radixEngine.deleteBranches();

		LedgerProof genesisLedgerHeader = LedgerProof.genesis(
			new AccumulatorState(0, hasher.hash(genesisTxns.get(0).getId())),
			genesisValidatorSet
		);
		if (!genesisLedgerHeader.isEndOfEpoch()) {
			throw new IllegalStateException("Genesis must be end of epoch");
		}
		radixEngine.execute(genesisTxns, LedgerAndBFTProof.create(genesisLedgerHeader), PermissionLevel.SYSTEM);
	}

	@Before
	public void setup() throws RadixEngineException {
		this.engineStore = new InMemoryEngineStore<>();
		Injector injector = Guice.createInjector(
			new RadixEngineCheckpointModule(),
			new RadixEngineModule(),
			new EmptyTransactionCheckModule(),
			new MockedGenesisAtomModule(),
			getExternalModule()
		);
		injector.injectMembers(this);
		setupGenesis();
	}

	private Txn systemUpdateTxn(long nextView, long nextEpoch) throws TxBuilderException {
		var builder = TxBuilder.newSystemBuilder(this.engineStore);
		if (nextEpoch >= 2) {
			builder.systemNextEpoch(0, nextEpoch - 1);
		} else {
			builder.systemNextView(nextView, 0, nextEpoch);
		}

		return builder.buildWithoutSignature();
	}

	private Txn systemUpdateCommand(long nextView, long nextEpoch) throws TxBuilderException {
		return systemUpdateTxn(nextView, nextEpoch);
	}

	private static Txn registerCommand(ECKeyPair keyPair) throws TxBuilderException {
		var address = new RadixAddress((byte) 0, keyPair.getPublicKey());
		return TxBuilder.newBuilder(address)
			.registerAsValidator()
			.signAndBuild(keyPair::sign);
	}

	@Test
	public void executing_non_epoch_high_view_should_return_no_validator_set() {
		// Action
		StateComputerResult result = sut.prepare(ImmutableList.of(), List.of(), 1, View.of(9), 0);

		// Assert
		assertThat(result.getSuccessfulCommands()).hasSize(1);
		assertThat(result.getFailedCommands()).isEmpty();
		assertThat(result.getNextValidatorSet()).isEmpty();
	}

	@Test
	public void executing_epoch_high_view_should_return_next_validator_set() {
		// Act
		StateComputerResult result = sut.prepare(ImmutableList.of(), List.of(), 1, View.of(10), 0);

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

		// Act
		StateComputerResult result = sut.prepare(ImmutableList.of(), List.of(txn), 1, View.of(10), 0);

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
		var txn = systemUpdateTxn(1, 1);
		var illegalTxn = TxLowLevelBuilder.newBuilder()
			.down(SubstateId.ofSubstate(txn.getId(), 1))
			.up(new SystemParticle(1, 2, 0))
			.buildWithoutSignature();

		// Act
		StateComputerResult result = sut.prepare(ImmutableList.of(), List.of(illegalTxn), 1, View.of(1), 0);

		// Assert
		assertThat(result.getSuccessfulCommands()).hasSize(1);
		assertThat(result.getFailedCommands()).hasValueSatisfying(
			new Condition<>(
				e -> {
					RadixEngineException ex = (RadixEngineException) e;
					return ex.getCmError().getErrorCode().equals(CMErrorCode.INVALID_EXECUTION_PERMISSION);
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
		LedgerProof ledgerProof = new LedgerProof(
			mock(BFTHeader.class),
			mock(BFTHeader.class),
			0,
			HashUtils.zero256(),
			LedgerHeader.create(0, View.of(11), new AccumulatorState(3, HashUtils.zero256()), 0),
			new TimestampedECDSASignatures()
		);
		VerifiedTxnsAndProof commandsAndProof = new VerifiedTxnsAndProof(
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
		LedgerProof ledgerProof = new LedgerProof(
			mock(BFTHeader.class),
			mock(BFTHeader.class),
			0,
			HashUtils.zero256(),
			LedgerHeader.create(0, View.of(9), new AccumulatorState(3, HashUtils.zero256()), 0),
			new TimestampedECDSASignatures()
		);
		VerifiedTxnsAndProof commandsAndProof = new VerifiedTxnsAndProof(
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
		ECKeyPair keyPair = ECKeyPair.generateNew();
		var cmd0 = systemUpdateCommand(0, 2);
		var cmd1 = registerCommand(keyPair);
		LedgerProof ledgerProof = new LedgerProof(
			mock(BFTHeader.class),
			mock(BFTHeader.class),
			0,
			HashUtils.zero256(),
			LedgerHeader.create(0, View.of(9), new AccumulatorState(3, HashUtils.zero256()), 0,
				BFTValidatorSet.from(Stream.of(BFTValidator.from(BFTNode.random(), UInt256.ONE)))
			),
			new TimestampedECDSASignatures()
		);
		VerifiedTxnsAndProof commandsAndProof = new VerifiedTxnsAndProof(
			ImmutableList.of(cmd1, cmd0),
			ledgerProof
		);

		// Act
		// Assert
		assertThatThrownBy(() -> sut.commit(commandsAndProof, null))
			.isInstanceOf(ByzantineQuorumException.class);
	}

	// TODO: should catch this and log it somewhere as proof of byzantine quorum
	@Test
	public void committing_epoch_change_when_there_shouldnt_be_one__should_fail() throws TxBuilderException {
		// Arrange
		var cmd0 = systemUpdateCommand(1, 1);
		LedgerProof ledgerProof = new LedgerProof(
			mock(BFTHeader.class),
			mock(BFTHeader.class),
			0,
			HashUtils.zero256(),
			LedgerHeader.create(0, View.of(9), new AccumulatorState(3, HashUtils.zero256()), 0,
				BFTValidatorSet.from(Stream.of(BFTValidator.from(BFTNode.random(), UInt256.ONE)))
			),
			new TimestampedECDSASignatures()
		);
		VerifiedTxnsAndProof commandsAndProof = new VerifiedTxnsAndProof(
			ImmutableList.of(cmd0),
			ledgerProof
		);

		// Act
		// Assert
		assertThatThrownBy(() -> sut.commit(commandsAndProof, null))
			.isInstanceOf(ByzantineQuorumException.class);
	}
}
