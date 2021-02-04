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

package com.radixdlt.store.berkeley;

import java.util.List;

import javax.inject.Inject;

import org.apache.commons.cli.ParseException;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.radix.database.DatabaseEnvironment;

import com.google.common.collect.ImmutableList;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Scopes;
import com.google.inject.TypeLiteral;
import com.radixdlt.CheckpointModule;
import com.radixdlt.CryptoModule;
import com.radixdlt.PersistenceModule;
import com.radixdlt.RadixEngineStoreModule;
import com.radixdlt.atommodel.Atom;
import com.radixdlt.consensus.BFTHeader;
import com.radixdlt.consensus.GenesisValidatorSetProvider;
import com.radixdlt.consensus.LedgerHeader;
import com.radixdlt.consensus.TimestampedECDSASignatures;
import com.radixdlt.consensus.VerifiedLedgerHeaderAndProof;
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
import com.radixdlt.middleware2.ClientAtom;
import com.radixdlt.middleware2.store.CommittedAtomsStore;
import com.radixdlt.properties.RuntimeProperties;
import com.radixdlt.statecomputer.AtomCommittedToLedger;
import com.radixdlt.statecomputer.CommittedAtom;
import com.radixdlt.store.NextCommittedLimitReachedException;
import com.radixdlt.utils.UInt256;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests to ensure that command batches read from {@link CommittedAtomsStore}
 * do not cross epoch boundaries.
 */
public class GetNextCommittedCommandsTest {
	@Rule
	public TemporaryFolder folder = new TemporaryFolder();

	private Injector injector;

	@Inject
	private CommittedAtomsStore committedAtomsStore;

	@Inject
	private Hasher hasher;

	@Before
	public void setup() {
		this.injector = Guice.createInjector(
			new CryptoModule(),
			new CheckpointModule(),
			new PersistenceModule(),
			new RadixEngineStoreModule(),
			new AbstractModule() {
				@Override
				protected void configure() {
					final RuntimeProperties runtimeProperties;
					try {
						runtimeProperties = new RuntimeProperties(new JSONObject(), new String[0]);
						runtimeProperties.set(
							"db.location",
							folder.getRoot().getAbsolutePath() + "/RADIXDB_TEST"
						);
					} catch (ParseException e) {
						throw new IllegalStateException();
					}
					bind(RuntimeProperties.class).toInstance(runtimeProperties);
					bind(SystemCounters.class).to(SystemCountersImpl.class).in(Scopes.SINGLETON);
					bind(GenesisValidatorSetProvider.class).toInstance(() -> BFTValidatorSet.from(List.of()));
					bind(new TypeLiteral<EventDispatcher<AtomCommittedToLedger>>() { }).toInstance(e -> { });
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

	@Test
	public void when_request_from_empty_store__null_returned() throws NextCommittedLimitReachedException {
		final var committedAtomsStore = this.injector.getInstance(CommittedAtomsStore.class);

		// No atoms generated

		final var commands = committedAtomsStore.getNextCommittedCommands(0, 100);

		assertThat(commands).isNull();
	}

	@Test
	public void when_request_over_epoch_boundary__commands_within_epoch_returned()
		throws NextCommittedLimitReachedException {
		generateAtoms(1, 1, 10);
		generateAtoms(2, 11, 10);

		final var commands = this.committedAtomsStore.getNextCommittedCommands(0, 100);

		assertThat(commands.getCommands())
			.hasSize(10);
	}

	@Test
	public void when_request_to_store_limit__commands_within_limit_returned()
		throws NextCommittedLimitReachedException {
		generateAtoms(1, 1, 10);

		final var commands = this.committedAtomsStore.getNextCommittedCommands(0, 100);

		assertThat(commands.getCommands())
			.hasSize(10);
	}

	@Test
	public void when_request_over_epoch_boundary_from_middle__commands_within_epoch_returned()
		throws NextCommittedLimitReachedException {
		generateAtoms(1, 1, 100);
		generateAtoms(2, 101, 100);

		final var commands = this.committedAtomsStore.getNextCommittedCommands(9, 100);

		assertThat(commands.getCommands())
			.hasSize(91);
	}

	@Test
	public void when_request_within_epoch__full_batch_returned()
		throws NextCommittedLimitReachedException {
		generateAtoms(1, 1, 200);
		generateAtoms(2, 201, 100);

		final var commands = this.committedAtomsStore.getNextCommittedCommands(9, 100);

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
		final var atom = generateCommittedAtom(epoch, view, stateVersion, endOfEpoch);
		this.committedAtomsStore.storeAtom(atom);
	}

	private CommittedAtom generateCommittedAtom(long epoch, View view, long stateVersion, boolean endOfEpoch) {
		final var atom = new Atom("Atom for " + stateVersion); // Make hash different
		final var clientAtom = ClientAtom.convertFromApiAtom(atom, this.hasher);

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
		final var committedAccumulatorState =  new AccumulatorState(stateVersion, HashUtils.random256());
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
		final var proof = new VerifiedLedgerHeaderAndProof(proposed, parent, stateVersion, committedVertexId, committedLedgerHeader, signatures);
		final var committedAtom = new CommittedAtom(clientAtom, stateVersion, proof);

		return committedAtom;
	}
}
