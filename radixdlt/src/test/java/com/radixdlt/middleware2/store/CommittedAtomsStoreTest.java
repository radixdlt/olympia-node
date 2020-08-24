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
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.constraintmachine.Spin;
import com.radixdlt.identifiers.AID;
import com.radixdlt.identifiers.EUID;
import com.radixdlt.middleware2.ClientAtom;
import com.radixdlt.statecomputer.ClientAtomToBinaryConverter;
import com.radixdlt.statecomputer.CommittedAtom;
import com.radixdlt.statecomputer.CommandToBinaryConverter;
import com.radixdlt.middleware2.store.CommittedAtomsStore.AtomIndexer;
import com.radixdlt.statecomputer.RadixEngineStateComputer.CommittedAtomSender;
import com.radixdlt.store.LedgerEntry;
import com.radixdlt.store.LedgerEntryStore;
import com.radixdlt.store.SearchCursor;
import com.radixdlt.ledger.CommittedCommand;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
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

	@Before
	public void setUp() {
		this.committedAtomSender = mock(CommittedAtomSender.class);
		this.store = mock(LedgerEntryStore.class);
		this.commandToBinaryConverter = mock(CommandToBinaryConverter.class);
		this.clientAtomToBinaryConverter = mock(ClientAtomToBinaryConverter.class);
		this.atomIndexer = mock(AtomIndexer.class);

		this.committedAtomsStore = new CommittedAtomsStore(
			committedAtomSender,
			store,
			commandToBinaryConverter,
			clientAtomToBinaryConverter,
			atomIndexer
		);
	}

	@Test
	public void when_get_atom_containing__then_should_return_atom() {
		Particle particle = mock(Particle.class);
		when(particle.euid()).thenReturn(EUID.ONE);
		// No type check issues with mocking generic here
		@SuppressWarnings("unchecked")
		Consumer<CommittedAtom> callback = mock(Consumer.class);
		SearchCursor searchCursor = mock(SearchCursor.class);
		AID aid = mock(AID.class);
		when(searchCursor.get()).thenReturn(aid);
		when(store.search(any(), any(), any())).thenReturn(searchCursor);
		LedgerEntry ledgerEntry = mock(LedgerEntry.class);
		when(ledgerEntry.getContent()).thenReturn(new byte[0]);

		VertexMetadata vertexMetadata = mock((VertexMetadata.class));
		Command command = mock(Command.class);
		ClientAtom clientAtom = mock(ClientAtom.class);
		when(command.map(any())).thenReturn(clientAtom);
		CommittedCommand committedCommand = new CommittedCommand(command, vertexMetadata);
		when(commandToBinaryConverter.toCommand(any())).thenReturn(committedCommand);
		when(store.get(eq(aid))).thenReturn(Optional.of(ledgerEntry));

		committedAtomsStore.getAtomContaining(particle, true, callback);
		verify(callback, times(1))
			.accept(argThat(a -> Objects.equals(a.getClientAtom(), clientAtom) && a.getVertexMetadata().equals(vertexMetadata)));
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