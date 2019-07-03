package com.radixdlt.client.core.ledger;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.radixdlt.client.atommodel.accounts.RadixAddress;
import com.radixdlt.client.core.atoms.Atom;
import com.radixdlt.client.core.atoms.AtomStatus;
import com.radixdlt.client.core.atoms.AtomStatusEvent;
import com.radixdlt.client.core.network.RadixNode;
import com.radixdlt.client.core.network.RadixNodeAction;
import com.radixdlt.client.core.network.actions.FetchAtomsObservationAction;
import com.radixdlt.client.core.network.actions.SubmitAtomStatusAction;
import java.util.stream.Stream;
import org.junit.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;

public class InMemoryAtomStoreReducerTest {
	@Test
	public void when_fetch_atoms_head_action_received__head_should_be_received_by_store() {
		InMemoryAtomStore atomStore = mock(InMemoryAtomStore.class);
		InMemoryAtomStoreReducer reducer = new InMemoryAtomStoreReducer(atomStore);
		RadixAddress address = mock(RadixAddress.class);
		RadixNode node = mock(RadixNode.class);
		reducer.reduce(FetchAtomsObservationAction.of("hi", address, node, AtomObservation.head()));

		verify(atomStore, times(1)).store(eq(address), argThat(AtomObservation::isHead));
	}

	@Test
	public void when_stored_action_received__a_soft_store_observation_should_be_stored() {
		InMemoryAtomStore atomStore = mock(InMemoryAtomStore.class);
		InMemoryAtomStoreReducer reducer = new InMemoryAtomStoreReducer(atomStore);
		RadixAddress address = mock(RadixAddress.class);
		final Atom atom = mock(Atom.class);
		when(atom.addresses()).thenReturn(Stream.of(address), Stream.of(address));
		RadixNode node = mock(RadixNode.class);
		RadixNodeAction action = SubmitAtomStatusAction.fromStatusNotification(
				"different-id",
				atom,
				node,
				new AtomStatusEvent(AtomStatus.STORED, null)
			);
		reducer.reduce(action);

		InOrder inOrder = Mockito.inOrder(atomStore, atomStore);
		inOrder.verify(atomStore).store(eq(address), argThat(o ->
			o.isStore()
				&& o.getAtom().equals(atom)
				&& o.getUpdateType().isSoft()
		));
		inOrder.verify(atomStore).store(eq(address), argThat(AtomObservation::isHead));
	}
}