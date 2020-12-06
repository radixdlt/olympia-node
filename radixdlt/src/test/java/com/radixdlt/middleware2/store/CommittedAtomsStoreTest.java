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

package com.radixdlt.middleware2.store;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.powermock.api.mockito.PowerMockito.when;

import com.google.common.collect.ImmutableList;
import com.radixdlt.consensus.Command;
import com.radixdlt.consensus.Sha256Hasher;
import com.radixdlt.consensus.VerifiedLedgerHeaderAndProof;
import com.radixdlt.constraintmachine.CMInstruction;
import com.radixdlt.constraintmachine.CMMicroInstruction;
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.constraintmachine.Spin;
import com.radixdlt.crypto.Hasher;
import com.radixdlt.identifiers.AID;
import com.radixdlt.middleware2.ClientAtom;
import com.radixdlt.serialization.Serialization;
import com.radixdlt.statecomputer.ClientAtomToBinaryConverter;
import com.radixdlt.middleware2.store.CommittedAtomsStore.AtomIndexer;
import com.radixdlt.statecomputer.RadixEngineStateComputer.CommittedAtomSender;
import com.radixdlt.store.LedgerEntry;
import com.radixdlt.store.LedgerEntryStore;
import com.radixdlt.store.SearchCursor;
import java.util.HashSet;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;

public class CommittedAtomsStoreTest {
	private CommittedAtomsStore committedAtomsStore;
	private LedgerEntryStore store;
	private CommandToBinaryConverter commandToBinaryConverter;
	private ClientAtomToBinaryConverter clientAtomToBinaryConverter;
	private CommittedAtomSender committedAtomSender;
	private AtomIndexer atomIndexer;
	private Serialization serialization;
	private Hasher hasher;

	@Before
	public void setUp() {
		this.committedAtomSender = mock(CommittedAtomSender.class);
		this.store = mock(LedgerEntryStore.class);
		this.commandToBinaryConverter = mock(CommandToBinaryConverter.class);
		this.clientAtomToBinaryConverter = mock(ClientAtomToBinaryConverter.class);
		this.atomIndexer = mock(AtomIndexer.class);
		this.serialization = mock(Serialization.class);
		this.hasher = Sha256Hasher.withDefaultSerialization();

		this.committedAtomsStore = new CommittedAtomsStore(
			committedAtomSender,
			store,
			commandToBinaryConverter,
			clientAtomToBinaryConverter,
			atomIndexer,
			serialization,
			hasher
		);
	}

	@Test
	public void when_compute_and_empty__then_should_return_initial_state() {
		when(serialization.getIdForClass(any())).thenReturn("test");
		when(store.search(any(), any(), any())).thenReturn(null);
		Object initial = mock(Object.class);
		Object result = committedAtomsStore.compute(Particle.class, initial, (o, v) -> {
			throw new RuntimeException();
		}, (o, v) -> {
			throw new RuntimeException();
		});

		assertThat(result).isEqualTo(initial);
	}

	@Test
	public void when_compute_and_not_empty__then_should_scan_and_compute() {
		when(serialization.getIdForClass(any())).thenReturn("test");

		// TODO: Cleanup this transformation mess
		SearchCursor searchCursor = mock(SearchCursor.class);
		when(store.search(any(), any(), any())).thenReturn(searchCursor);
		AID aid = mock(AID.class);
		when(searchCursor.get()).thenReturn(aid);
		LedgerEntry ledgerEntry = mock(LedgerEntry.class);
		when(store.get(eq(aid))).thenReturn(Optional.of(ledgerEntry));
		StoredCommittedCommand committedCommand = mock(StoredCommittedCommand.class);
		Command command = mock(Command.class);
		ClientAtom clientAtom = mock(ClientAtom.class);
		CMInstruction cmInstruction = mock(CMInstruction.class);
		when(clientAtom.getCMInstruction()).thenReturn(cmInstruction);
		when(cmInstruction.getMicroInstructions())
			.thenReturn(ImmutableList.of(
				CMMicroInstruction.checkSpinAndPush(mock(Particle.class), Spin.NEUTRAL)
			));
		when(command.map(any())).thenReturn(clientAtom);
		when(committedCommand.getCommand()).thenReturn(command);
		when(commandToBinaryConverter.toCommand(any())).thenReturn(committedCommand);
		HashSet<Particle> result = committedAtomsStore.compute(Particle.class, new HashSet<>(), (s, v) -> {
			s.add(v);
			return s;
		}, (s, v) -> {
			s.remove(v);
			return s;
		});

		assertThat(result).hasSize(1);
	}


	@Test
	public void when_get_spin_and_particle_exists__then_should_return_spin() {
		Particle particle = mock(Particle.class);
		AID aid = mock(AID.class);
		when(store.contains(any(), any(), any(), any())).thenReturn(true);
		LedgerEntry ledgerEntry = mock(LedgerEntry.class);
		when(ledgerEntry.getContent()).thenReturn(new byte[0]);
		StoredCommittedCommand committedCommand = mock(StoredCommittedCommand.class);
		Command command = mock(Command.class);
		when(command.map(any())).thenReturn(mock(ClientAtom.class));
		when(committedCommand.getCommand()).thenReturn(command);
		when(committedCommand.getStateAndProof()).thenReturn(mock(VerifiedLedgerHeaderAndProof.class));
		when(commandToBinaryConverter.toCommand(any())).thenReturn(committedCommand);
		when(store.get(eq(aid))).thenReturn(Optional.of(ledgerEntry));

		assertThat(committedAtomsStore.getSpin(particle)).isEqualTo(Spin.DOWN);
	}
}