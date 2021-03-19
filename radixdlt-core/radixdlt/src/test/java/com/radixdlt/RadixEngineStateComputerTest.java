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
import com.google.common.collect.ImmutableSet;
import com.google.common.hash.HashCode;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Names;
import com.radixdlt.atom.AtomBuilder;
import com.radixdlt.atommodel.system.SystemParticle;
import com.radixdlt.atommodel.validators.RegisteredValidatorParticle;
import com.radixdlt.atommodel.validators.UnregisteredValidatorParticle;
import com.radixdlt.consensus.BFTHeader;
import com.radixdlt.consensus.Command;
import com.radixdlt.consensus.LedgerHeader;
import com.radixdlt.consensus.TimestampedECDSASignatures;
import com.radixdlt.consensus.VerifiedLedgerHeaderAndProof;
import com.radixdlt.consensus.Sha256Hasher;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.BFTValidator;
import com.radixdlt.consensus.bft.BFTValidatorSet;
import com.radixdlt.consensus.bft.PersistentVertexStore;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.constraintmachine.CMErrorCode;
import com.radixdlt.constraintmachine.CMMicroInstruction;
import com.radixdlt.constraintmachine.PermissionLevel;
import com.radixdlt.constraintmachine.Spin;
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
import com.radixdlt.ledger.VerifiedCommandsAndProof;
import com.radixdlt.mempool.Mempool;
import com.radixdlt.mempool.MempoolAddFailure;
import com.radixdlt.mempool.MempoolAddSuccess;
import com.radixdlt.mempool.MempoolMaxSize;
import com.radixdlt.mempool.MempoolThrottleMs;
import com.radixdlt.atom.ParticleGroup;
import com.radixdlt.atom.Atom;
import com.radixdlt.atom.LedgerAtom;
import com.radixdlt.middleware2.store.RadixEngineAtomicCommitManager;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.Serialization;
import com.radixdlt.statecomputer.CommittedAtom;
import com.radixdlt.statecomputer.EpochCeilingView;
import com.radixdlt.statecomputer.InvalidProposedCommand;
import com.radixdlt.statecomputer.MaxValidators;
import com.radixdlt.statecomputer.AtomsRemovedFromMempool;
import com.radixdlt.statecomputer.MinValidators;
import com.radixdlt.statecomputer.RadixEngineModule;
import com.radixdlt.statecomputer.RadixEngineStateComputer;

import com.radixdlt.statecomputer.RadixEngineStateComputer.RadixEngineCommand;
import com.radixdlt.statecomputer.RegisteredValidators;
import com.radixdlt.statecomputer.Stakes;
import com.radixdlt.statecomputer.ValidatorSetBuilder;
import com.radixdlt.statecomputer.checkpoint.Genesis;
import com.radixdlt.statecomputer.checkpoint.MockedGenesisAtomModule;
import com.radixdlt.statecomputer.checkpoint.RadixEngineCheckpointModule;
import com.radixdlt.store.EngineStore;
import com.radixdlt.store.InMemoryEngineStore;
import com.radixdlt.utils.TypedMocks;
import com.radixdlt.utils.UInt256;

import java.util.stream.Stream;
import org.assertj.core.api.Condition;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

public class RadixEngineStateComputerTest {
	@Inject
	@Genesis
	private Atom genesisAtom;

	@Inject
	private RadixEngine<LedgerAtom> radixEngine;

	@Inject
	private RadixEngineStateComputer sut;

	@Inject
	private ValidatorSetBuilder validatorSetBuilder;


	private Serialization serialization = DefaultSerialization.getInstance();
	private EngineStore<LedgerAtom> engineStore;
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
				bind(new TypeLiteral<EngineStore<LedgerAtom>>() { }).toInstance(engineStore);
				bind(RadixEngineAtomicCommitManager.class).toInstance(mock(RadixEngineAtomicCommitManager.class));
				bind(PersistentVertexStore.class).toInstance(mock(PersistentVertexStore.class));
				bindConstant().annotatedWith(Names.named("magic")).to(0);
				bindConstant().annotatedWith(MinValidators.class).to(1);
				bindConstant().annotatedWith(MaxValidators.class).to(100);
				bindConstant().annotatedWith(MempoolMaxSize.class).to(10);
				bindConstant().annotatedWith(MempoolThrottleMs.class).to(10L);
				bind(View.class).annotatedWith(EpochCeilingView.class).toInstance(View.of(10));
				bind(Mempool.class).toInstance(mock(Mempool.class));

				bind(new TypeLiteral<EventDispatcher<MempoolAddSuccess>>() { })
						.toInstance(TypedMocks.rmock(EventDispatcher.class));
				bind(new TypeLiteral<EventDispatcher<MempoolAddFailure>>() { })
					.toInstance(TypedMocks.rmock(EventDispatcher.class));
				bind(new TypeLiteral<EventDispatcher<InvalidProposedCommand>>() { })
						.toInstance(TypedMocks.rmock(EventDispatcher.class));
				bind(new TypeLiteral<EventDispatcher<AtomsRemovedFromMempool>>() { })
						.toInstance(TypedMocks.rmock(EventDispatcher.class));

				bind(SystemCounters.class).to(SystemCountersImpl.class);
			}
		};
	}

	private void setupGenesis() throws RadixEngineException {
		RadixEngine.RadixEngineBranch<LedgerAtom> branch = radixEngine.transientBranch();
		branch.execute(genesisAtom, PermissionLevel.SYSTEM);
		final var genesisValidatorSet = validatorSetBuilder.buildValidatorSet(
			branch.getComputedState(RegisteredValidators.class),
			branch.getComputedState(Stakes.class)
		);
		radixEngine.deleteBranches();

		byte[] payload = serialization.toDson(genesisAtom, DsonOutput.Output.ALL);
		Command command = new Command(payload);
		VerifiedLedgerHeaderAndProof genesisLedgerHeader = VerifiedLedgerHeaderAndProof.genesis(
			hasher.hash(command),
			genesisValidatorSet
		);
		if (!genesisLedgerHeader.isEndOfEpoch()) {
			throw new IllegalStateException("Genesis must be end of epoch");
		}
		CommittedAtom committedAtom = CommittedAtom.create(
			genesisAtom,
			genesisLedgerHeader
		);
		radixEngine.execute(committedAtom, PermissionLevel.SYSTEM);
	}

	@Before
	public void setup() throws RadixEngineException {
		this.engineStore = new InMemoryEngineStore<>();
		Injector injector = Guice.createInjector(
			new RadixEngineCheckpointModule(),
			new RadixEngineModule(),
			new NoFeeModule(),
			new MockedGenesisAtomModule(),
			getExternalModule()
		);
		injector.injectMembers(this);
		setupGenesis();
	}

	private static RadixEngineCommand systemUpdateCommand(long prevView, long nextView, long nextEpoch) {
		SystemParticle lastSystemParticle = new SystemParticle(1, prevView, 1);
		SystemParticle nextSystemParticle = new SystemParticle(nextEpoch, nextView, 1);
		Atom atom = Atom.create(
			ImmutableList.of(
				CMMicroInstruction.checkSpinAndPush(lastSystemParticle, Spin.UP),
				CMMicroInstruction.checkSpinAndPush(nextSystemParticle, Spin.NEUTRAL),
				CMMicroInstruction.particleGroup()
			)
		);
		final byte[] payload = DefaultSerialization.getInstance().toDson(atom, Output.ALL);
		Command cmd = new Command(payload);
		return new RadixEngineCommand(cmd, hasher.hash(cmd), atom, PermissionLevel.USER);
	}

	private static RadixEngineCommand registerCommand(ECKeyPair keyPair) {
		RadixAddress address = new RadixAddress((byte) 0, keyPair.getPublicKey());
		RegisteredValidatorParticle registeredValidatorParticle = new RegisteredValidatorParticle(
			address, ImmutableSet.of(), 1
		);
		UnregisteredValidatorParticle unregisteredValidatorParticle = new UnregisteredValidatorParticle(
			address, 0
		);
		ParticleGroup particleGroup = ParticleGroup.builder()
			.virtualSpinDown(unregisteredValidatorParticle)
			.spinUp(registeredValidatorParticle)
			.build();
		AtomBuilder atom = Atom.newBuilder();
		atom.addParticleGroup(particleGroup);
		HashCode hashToSign = atom.computeHashToSign();
		atom.setSignature(keyPair.euid(), keyPair.sign(hashToSign));
		Atom clientAtom = atom.buildAtom();
		final byte[] payload = DefaultSerialization.getInstance().toDson(clientAtom, Output.ALL);
		Command cmd = new Command(payload);
		return new RadixEngineCommand(cmd, hasher.hash(cmd), clientAtom, PermissionLevel.USER);
	}

	@Test
	public void executing_non_epoch_high_view_should_return_no_validator_set() {
		// Action
		StateComputerResult result = sut.prepare(ImmutableList.of(), null, 1, View.of(9), 1);

		// Assert
		assertThat(result.getSuccessfulCommands()).hasSize(1);
		assertThat(result.getFailedCommands()).isEmpty();
		assertThat(result.getNextValidatorSet()).isEmpty();
	}

	@Test
	public void executing_epoch_high_view_should_return_next_validator_set() {
		// Act
		StateComputerResult result = sut.prepare(ImmutableList.of(), null, 1, View.of(10), 1);

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
	public void executing_epoch_high_view_with_register_should_not_return_new_next_validator_set() {
		// Arrange
		ECKeyPair keyPair = ECKeyPair.generateNew();
		RadixEngineCommand cmd = registerCommand(keyPair);
		BFTNode node = BFTNode.create(keyPair.getPublicKey());

		// Act
		StateComputerResult result = sut.prepare(ImmutableList.of(), cmd.command(), 1, View.of(10), 1);

		// Assert
		assertThat(result.getSuccessfulCommands()).hasSize(1); // since high view, command is not executed
		assertThat(result.getNextValidatorSet()).hasValueSatisfying(s -> {
			assertThat(s.getValidators()).hasSize(2);
			assertThat(s.getValidators()).extracting(BFTValidator::getNode).doesNotContain(node);
		});
	}

	@Test
	@Ignore("Difficult to include staking. Refactor then reenable")
	public void preparing_epoch_high_view_with_previous_registered_should_return_new_next_validator_set() {
		// Arrange
		RadixEngineCommand cmd = registerCommand(unregisteredNode);
		BFTNode node = BFTNode.create(unregisteredNode.getPublicKey());

		// Act
		StateComputerResult result = sut.prepare(ImmutableList.of(cmd), null, 1, View.of(10), 1);

		// Assert
		assertThat(result.getSuccessfulCommands()).hasSize(1);
		assertThat(result.getFailedCommands()).isEmpty();
		assertThat(result.getNextValidatorSet()).hasValueSatisfying(s -> {
			assertThat(s.getValidators()).hasSize(3);
			assertThat(s.getValidators()).extracting(BFTValidator::getNode).contains(node);
		});
	}

	@Test
	public void preparing_system_update_from_vertex_should_fail() {
		// Arrange
		RadixEngineCommand cmd = systemUpdateCommand(1, 2, 1);

		// Act
		StateComputerResult result = sut.prepare(ImmutableList.of(), cmd.command(), 1, View.of(1), 1);

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
	public void committing_epoch_high_views_should_fail() {
		// Arrange
		RadixEngineCommand cmd0 = systemUpdateCommand(0, 10, 1);
		VerifiedLedgerHeaderAndProof verifiedLedgerHeaderAndProof = new VerifiedLedgerHeaderAndProof(
			mock(BFTHeader.class),
			mock(BFTHeader.class),
			0,
			HashUtils.zero256(),
			LedgerHeader.create(0, View.of(11), new AccumulatorState(3, HashUtils.zero256()), 1),
			new TimestampedECDSASignatures()
		);
		VerifiedCommandsAndProof commandsAndProof = new VerifiedCommandsAndProof(
			ImmutableList.of(cmd0.command()),
			verifiedLedgerHeaderAndProof
		);

		// Act
		// Assert
		assertThatThrownBy(() -> sut.commit(commandsAndProof, null))
			.isInstanceOf(ByzantineQuorumException.class);
	}

	// TODO: should catch this and log it somewhere as proof of byzantine quorum
	@Test
	public void committing_epoch_change_with_additional_cmds_should_fail() {
		// Arrange
		ECKeyPair keyPair = ECKeyPair.generateNew();
		RadixEngineCommand cmd0 = systemUpdateCommand(0, 0, 2);
		RadixEngineCommand cmd1 = registerCommand(keyPair);
		VerifiedLedgerHeaderAndProof verifiedLedgerHeaderAndProof = new VerifiedLedgerHeaderAndProof(
			mock(BFTHeader.class),
			mock(BFTHeader.class),
			0,
			HashUtils.zero256(),
			LedgerHeader.create(0, View.of(9), new AccumulatorState(3, HashUtils.zero256()), 1),
			new TimestampedECDSASignatures()
		);
		VerifiedCommandsAndProof commandsAndProof = new VerifiedCommandsAndProof(
			ImmutableList.of(cmd0.command(), cmd1.command()),
			verifiedLedgerHeaderAndProof
		);

		// Act
		// Assert
		assertThatThrownBy(() -> sut.commit(commandsAndProof, null))
			.isInstanceOf(ByzantineQuorumException.class);
	}

	// TODO: should catch this and log it somewhere as proof of byzantine quorum
	@Test
	public void committing_epoch_change_with_different_validator_signed_should_fail() {
		// Arrange
		ECKeyPair keyPair = ECKeyPair.generateNew();
		RadixEngineCommand cmd0 = systemUpdateCommand(0, 0, 2);
		RadixEngineCommand cmd1 = registerCommand(keyPair);
		VerifiedLedgerHeaderAndProof verifiedLedgerHeaderAndProof = new VerifiedLedgerHeaderAndProof(
			mock(BFTHeader.class),
			mock(BFTHeader.class),
			0,
			HashUtils.zero256(),
			LedgerHeader.create(0, View.of(9), new AccumulatorState(3, HashUtils.zero256()), 1,
				BFTValidatorSet.from(Stream.of(BFTValidator.from(BFTNode.random(), UInt256.ONE)))
			),
			new TimestampedECDSASignatures()
		);
		VerifiedCommandsAndProof commandsAndProof = new VerifiedCommandsAndProof(
			ImmutableList.of(cmd1.command(), cmd0.command()),
			verifiedLedgerHeaderAndProof
		);

		// Act
		// Assert
		assertThatThrownBy(() -> sut.commit(commandsAndProof, null))
			.isInstanceOf(ByzantineQuorumException.class);
	}
}
