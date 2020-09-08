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
import com.radixdlt.consensus.VertexMetadata;
import com.radixdlt.constraintmachine.CMInstruction;
import com.radixdlt.constraintmachine.CMMicroInstruction;
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.constraintmachine.Spin;
import com.radixdlt.identifiers.AID;
import com.radixdlt.identifiers.EUID;
import com.radixdlt.middleware2.ClientAtom;
import com.radixdlt.serialization.Serialization;
import com.radixdlt.statecomputer.ClientAtomToBinaryConverter;
import com.radixdlt.statecomputer.CommandToBinaryConverter;
import com.radixdlt.middleware2.store.CommittedAtomsStore.AtomIndexer;
import com.radixdlt.statecomputer.RadixEngineStateComputer.CommittedAtomSender;
import com.radixdlt.store.LedgerEntry;
import com.radixdlt.store.LedgerEntryStore;
import com.radixdlt.store.SearchCursor;
import com.radixdlt.ledger.CommittedCommand;
import java.util.HashSet;
import java.util.Optional;
import java.util.stream.Stream;
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

	@Before
	public void setUp() {
		this.committedAtomSender = mock(CommittedAtomSender.class);
		this.store = mock(LedgerEntryStore.class);
		this.commandToBinaryConverter = mock(CommandToBinaryConverter.class);
		this.clientAtomToBinaryConverter = mock(ClientAtomToBinaryConverter.class);
		this.atomIndexer = mock(AtomIndexer.class);
		this.serialization = mock(Serialization.class);

		this.committedAtomsStore = new CommittedAtomsStore(
			committedAtomSender,
			store,
			commandToBinaryConverter,
			clientAtomToBinaryConverter,
			atomIndexer,
			serialization
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
		CommittedCommand committedCommand = mock(CommittedCommand.class);
		Command command = mock(Command.class);
		ClientAtom clientAtom = mock(ClientAtom.class);
		CMInstruction cmInstruction = mock(CMInstruction.class);
		when(clientAtom.getCMInstruction()).thenReturn(cmInstruction);
		when(cmInstruction.getMicroInstructions())
			.thenReturn(ImmutableList.of(
				CMMicroInstruction.checkSpin(mock(Particle.class), Spin.NEUTRAL),
				CMMicroInstruction.push(mock(Particle.class))
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
		when(particle.euid()).thenReturn(EUID.ONE);
		SearchCursor searchCursor = mock(SearchCursor.class);
		AID aid = mock(AID.class);
		when(searchCursor.get()).thenReturn(aid);
		when(store.search(any(), any(), any())).thenReturn(searchCursor);
		LedgerEntry ledgerEntry = mock(LedgerEntry.class);
		when(ledgerEntry.getContent()).thenReturn(new byte[0]);
		CommittedCommand committedCommand = mock(CommittedCommand.class);
		when(committedCommand.getCommand()).thenReturn(mock(Command.class));
		when(committedCommand.getVertexMetadata()).thenReturn(mock(VertexMetadata.class));
		when(commandToBinaryConverter.toCommand(any())).thenReturn(committedCommand);
		when(store.get(eq(aid))).thenReturn(Optional.of(ledgerEntry));

		assertThat(committedAtomsStore.getSpin(particle)).isEqualTo(Spin.DOWN);
	}

	@Test
	public void when_get_committed_atoms__should_return_atoms() {
		ImmutableList<AID> aids = ImmutableList.of(mock(AID.class), mock(AID.class), mock(AID.class), mock(AID.class));
		ImmutableList<LedgerEntry> entries = Stream.generate(() -> mock(LedgerEntry.class))
			.limit(4)
			.collect(ImmutableList.toImmutableList());
		when(this.store.getNextCommittedLedgerEntries(eq(3L), eq(4)))
				.thenReturn(entries);
		for (int i = 0; i < aids.size(); i++) {
			when(this.store.get(eq(aids.get(i)))).thenReturn(Optional.of(entries.get(i)));
			when(entries.get(i).getContent()).thenReturn(new byte[i]);
			when(this.commandToBinaryConverter.toCommand(any())).thenReturn(mock(CommittedCommand.class));
		}

		assertThat(this.committedAtomsStore.getCommittedCommands(3, 4)).hasSize(4);
	}
}