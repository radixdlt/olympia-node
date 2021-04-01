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
 */

package com.radixdlt.store.berkeley;

import java.util.List;

import javax.inject.Inject;

import com.google.inject.name.Names;
import com.radixdlt.atom.Atom;
import com.radixdlt.atom.TxLowLevelBuilder;
import com.radixdlt.ledger.DtoLedgerHeaderAndProof;
import com.radixdlt.statecomputer.LedgerAndBFTProof;
import com.radixdlt.statecomputer.checkpoint.Genesis;
import com.radixdlt.statecomputer.checkpoint.MockedGenesisAtomModule;
import com.radixdlt.statecomputer.checkpoint.RadixEngineCheckpointModule;
import com.radixdlt.atomos.RRIParticle;
import com.radixdlt.identifiers.RRI;
import com.radixdlt.identifiers.RadixAddress;
import com.radixdlt.store.DatabaseCacheSize;
import com.radixdlt.store.DatabaseLocation;
import com.radixdlt.store.EngineStore;
import com.radixdlt.sync.CommittedReader;
import com.radixdlt.utils.Pair;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.google.common.collect.ImmutableList;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Scopes;
import com.google.inject.TypeLiteral;
import com.radixdlt.CryptoModule;
import com.radixdlt.RadixEngineStoreModule;
import com.radixdlt.consensus.BFTHeader;
import com.radixdlt.consensus.LedgerHeader;
import com.radixdlt.consensus.TimestampedECDSASignatures;
import com.radixdlt.consensus.LedgerProof;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.BFTValidator;
import com.radixdlt.consensus.bft.BFTValidatorSet;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.consensus.safety.PersistentSafetyStateStore;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.counters.SystemCountersImpl;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.HashUtils;
import com.radixdlt.crypto.Hasher;
import com.radixdlt.environment.EventDispatcher;
import com.radixdlt.ledger.AccumulatorState;
import com.radixdlt.statecomputer.AtomsCommittedToLedger;
import com.radixdlt.store.DatabaseEnvironment;
import com.radixdlt.store.PersistenceModule;
import com.radixdlt.utils.UInt256;


import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests to ensure that command batches read from {@link CommittedReader}
 * do not cross epoch boundaries.
 */
public class GetNextCommittedCommandsTest {
	@Rule
	public TemporaryFolder folder = new TemporaryFolder();

	private Injector injector;

	@Inject
	private EngineStore<LedgerAndBFTProof> committedAtomsStore;

	@Inject
	private CommittedReader committedReader;

	@Inject
	private Hasher hasher;

	@Before
	public void setup() {
		this.injector = Guice.createInjector(
			new CryptoModule(),
			new MockedGenesisAtomModule(),
			new PersistenceModule(),
			new RadixEngineCheckpointModule(),
			new RadixEngineStoreModule(),
			new AbstractModule() {
				@Override
				protected void configure() {
					bind(ECKeyPair.class).annotatedWith(Names.named("universeKey")).toInstance(ECKeyPair.generateNew());
					bindConstant().annotatedWith(DatabaseLocation.class).to(folder.getRoot().getAbsolutePath());
					bindConstant().annotatedWith(DatabaseCacheSize.class).to(0L);
					bind(SystemCounters.class).to(SystemCountersImpl.class).in(Scopes.SINGLETON);
					bind(new TypeLiteral<EventDispatcher<AtomsCommittedToLedger>>() { }).toInstance(e -> { });
					bind(new TypeLiteral<List<BFTNode>>() { }).toInstance(List.of());
					bind(new TypeLiteral<ImmutableList<ECKeyPair>>() { }).annotatedWith(Genesis.class)
						.toInstance(ImmutableList.of());
				}
			}
		);
		this.injector.injectMembers(this);
	}

	@After
	public void teardown() {
		if (this.injector != null) {
			this.injector.getInstance(BerkeleyLedgerEntryStore.class).close();
			this.injector.getInstance(PersistentSafetyStateStore.class).close();
			this.injector.getInstance(DatabaseEnvironment.class).stop();
		}
	}

	private static DtoLedgerHeaderAndProof mockedProof(long stateVersion) {
		var headerAndProof = mock(DtoLedgerHeaderAndProof.class);
		var ledgerHeader = mock(LedgerHeader.class);
		var accumulatorState = mock(AccumulatorState.class);
		when(accumulatorState.getStateVersion()).thenReturn(stateVersion);
		when(ledgerHeader.getAccumulatorState()).thenReturn(accumulatorState);
		when(headerAndProof.getLedgerHeader()).thenReturn(ledgerHeader);
		return headerAndProof;
	}

	@Test
	public void when_request_from_empty_store__null_returned() {
		// No atoms generated
		final var commands = this.committedReader.getNextCommittedCommands(mockedProof(0));

		assertThat(commands).isNull();
	}

	@Test
	public void when_request_over_epoch_boundary__commands_within_epoch_returned() {
		generateAtoms(0, 0, 1);
		generateAtoms(1, 1, 10);
		generateAtoms(2, 11, 10);

		final var commands = this.committedReader.getNextCommittedCommands(mockedProof(0));

		assertThat(commands.getCommands())
			.hasSize(10);
	}

	@Test
	public void when_request_at_epoch_boundary__single_command_returned() {
		generateAtoms(0, 0, 1);
		generateAtoms(1, 1, 10);
		generateAtoms(2, 11, 10);

		final var commands = this.committedReader.getNextCommittedCommands(mockedProof(9));

		assertThat(commands.getCommands())
			.hasSize(1);
	}

	@Test
	public void when_request_to_store_limit__commands_within_limit_returned() {
		generateAtoms(0, 0, 1);
		generateAtoms(1, 1, 10);

		final var commands = this.committedReader.getNextCommittedCommands(mockedProof(0));

		assertThat(commands.getCommands())
			.hasSize(10);
	}

	@Test
	public void when_request_over_epoch_boundary_from_middle__commands_within_epoch_returned() {
		generateAtoms(0, 0, 1);
		generateAtoms(1, 1, 100);
		generateAtoms(2, 101, 100);

		final var commands = this.committedReader.getNextCommittedCommands(mockedProof(9));

		assertThat(commands.getCommands())
			.hasSize(91);
	}

	@Test
	public void when_request_within_epoch__full_batch_returned() {
		generateAtoms(0, 0, 1);
		generateAtoms(1, 1, 200);
		generateAtoms(2, 201, 100);

		final var commands = this.committedReader.getNextCommittedCommands(mockedProof(100));

		assertThat(commands.getCommands())
			.hasSize(100);
	}

	private void generateAtoms(long epoch, long startStateVersion, int n) {
		var stateVersion = startStateVersion;
		var view = View.of(1);
		for (int i = 0; i < n - 1; ++i) {
			generateAtom(epoch, view, stateVersion, false);
			stateVersion += 1;
			view = view.next();
		}
		generateAtom(epoch, view, stateVersion, true);
	}

	private void generateAtom(long epoch, View view, long stateVersion, boolean endOfEpoch) {
		final var atomAndProof = generateCommittedAtom(epoch, view, stateVersion, endOfEpoch);
		var txn = this.committedAtomsStore.createTransaction();
		this.committedAtomsStore.storeAtom(txn, atomAndProof.getFirst());
		var meta = LedgerAndBFTProof.create(atomAndProof.getSecond());
		this.committedAtomsStore.storeMetadata(txn, meta);
		txn.commit();
	}

	private Pair<Atom, LedgerProof> generateCommittedAtom(long epoch, View view, long stateVersion, boolean endOfEpoch) {
		var rri = RRI.of(new RadixAddress((byte) 0, ECKeyPair.generateNew().getPublicKey()), "Hi");
		final var atom = TxLowLevelBuilder.newBuilder()
			.message("Atom for " + stateVersion)
			.up(new RRIParticle(rri))
			.particleGroup()
			.buildWithoutSignature();

		final var proposedVertexId = HashUtils.random256();
		final var proposedView = view.next().next();
		final var proposedAccumulatorState = new AccumulatorState(stateVersion + 2, HashUtils.random256());
		final var proposedLedgerHeader = LedgerHeader.create(epoch, proposedView, proposedAccumulatorState, System.currentTimeMillis());
		final var proposed = new BFTHeader(proposedView, proposedVertexId, proposedLedgerHeader);
		final var parentVertexId = HashUtils.random256();
		final var parentView = view.next();
		final var parentAccumulatorState = new AccumulatorState(stateVersion + 1, HashUtils.random256());
		final var parentLedgerHeader = LedgerHeader.create(epoch, parentView, parentAccumulatorState, System.currentTimeMillis());
		final var parent = new BFTHeader(parentView, parentVertexId, parentLedgerHeader);
		final var committedVertexId = HashUtils.random256();
		final var committedAccumulatorState = new AccumulatorState(stateVersion, HashUtils.random256());
		final LedgerHeader committedLedgerHeader;
		if (endOfEpoch) {
			// Requires a non-empty validator set to survive serialisation
			final var node = BFTNode.create(ECKeyPair.generateNew().getPublicKey());
			final var validator = BFTValidator.from(node, UInt256.ONE);
			final var validatorSet = BFTValidatorSet.from(ImmutableList.of(validator));
			committedLedgerHeader = LedgerHeader.create(epoch, view, committedAccumulatorState, System.currentTimeMillis(), validatorSet);
		} else {
			committedLedgerHeader = LedgerHeader.create(epoch, view, committedAccumulatorState, System.currentTimeMillis());
		}
		final var signatures = new TimestampedECDSASignatures();
		final var proof = new LedgerProof(
			proposed,
			parent,
			stateVersion,
			committedVertexId,
			committedLedgerHeader,
			signatures
		);
		return Pair.of(atom, proof);
	}
}
