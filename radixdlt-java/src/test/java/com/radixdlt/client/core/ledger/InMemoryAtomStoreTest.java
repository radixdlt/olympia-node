package com.radixdlt.client.core.ledger;

import com.radixdlt.client.core.atoms.Atom;
import com.radixdlt.client.core.ledger.AtomObservation.AtomObservationUpdateType;
import com.radixdlt.client.core.ledger.AtomObservation.Type;
import com.radixdlt.client.core.atoms.RadixHash;
import org.junit.Test;

import com.radixdlt.client.atommodel.accounts.RadixAddress;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.reactivex.observers.TestObserver;

public class InMemoryAtomStoreTest {
	@Test
	public void subscribeBeforeStoreAtomTest() {
		InMemoryAtomStore inMemoryAtomStore = new InMemoryAtomStore();
		Atom atom = mock(Atom.class);
		RadixHash hash = mock(RadixHash.class);
		when(atom.getHash()).thenReturn(hash);
		AtomObservation atomObservation = mock(AtomObservation.class);
		when(atomObservation.getAtom()).thenReturn(atom);
		when(atomObservation.isStore()).thenReturn(true);
		when(atomObservation.isHead()).thenReturn(false);
		when(atomObservation.getType()).thenReturn(Type.STORE);
		when(atomObservation.getUpdateType()).thenReturn(AtomObservationUpdateType.of(Type.STORE, false));

		TestObserver<AtomObservation> testObserver = TestObserver.create();

		RadixAddress address = mock(RadixAddress.class);

		inMemoryAtomStore.getAtoms(address).subscribe(testObserver);
		inMemoryAtomStore.store(address, atomObservation);

		testObserver.assertValue(atomObservation);
	}

	@Test
	public void subscribeAfterStoreAtomTest() {
		InMemoryAtomStore inMemoryAtomStore = new InMemoryAtomStore();
		AtomObservation atomObservation = mock(AtomObservation.class);
		Atom atom = mock(Atom.class);
		RadixHash hash = mock(RadixHash.class);
		when(atom.getHash()).thenReturn(hash);
		when(atomObservation.getAtom()).thenReturn(atom);
		when(atomObservation.isStore()).thenReturn(true);
		when(atomObservation.isHead()).thenReturn(false);
		when(atomObservation.getType()).thenReturn(Type.STORE);
		when(atomObservation.getUpdateType()).thenReturn(AtomObservationUpdateType.of(Type.STORE, false));

		TestObserver<AtomObservation> testObserver = TestObserver.create();
		RadixAddress address = mock(RadixAddress.class);

		inMemoryAtomStore.store(address, atomObservation);
		inMemoryAtomStore.getAtoms(address).subscribe(testObserver);

		testObserver.assertValue(atomObservation);
	}

	@Test
	public void when_receiving_atom_deletes_for_atoms_which_have_not_been_seen__store_should_not_propagate_delete_event() {
		InMemoryAtomStore inMemoryAtomStore = new InMemoryAtomStore();
		Atom atom = mock(Atom.class);
		RadixHash hash = mock(RadixHash.class);
		when(hash.toString()).thenReturn("hash");
		when(atom.getHash()).thenReturn(hash);

		AtomObservation deleteObservation = mock(AtomObservation.class);
		when(deleteObservation.getAtom()).thenReturn(atom);
		when(deleteObservation.isStore()).thenReturn(false);
		when(deleteObservation.isHead()).thenReturn(false);
		when(deleteObservation.getType()).thenReturn(Type.DELETE);
		when(deleteObservation.getUpdateType()).thenReturn(AtomObservationUpdateType.of(Type.DELETE, false));


		Atom atom2 = mock(Atom.class);
		RadixHash hash2 = mock(RadixHash.class);
		when(hash2.toString()).thenReturn("hash2");
		when(atom2.getHash()).thenReturn(hash2);

		AtomObservation storeObservation = mock(AtomObservation.class);
		when(storeObservation.getAtom()).thenReturn(atom2);
		when(storeObservation.isStore()).thenReturn(true);
		when(storeObservation.isHead()).thenReturn(false);
		when(storeObservation.getType()).thenReturn(Type.STORE);
		when(storeObservation.getUpdateType()).thenReturn(AtomObservationUpdateType.of(Type.STORE, false));

		RadixAddress address = mock(RadixAddress.class);
		inMemoryAtomStore.store(address, deleteObservation);
		inMemoryAtomStore.store(address, storeObservation);

		TestObserver<AtomObservation> testObserver = TestObserver.create();
		inMemoryAtomStore.getAtoms(address).subscribe(testObserver);
		testObserver.assertValue(storeObservation);
	}

	@Test
	public void when_receiving_atom_store_then_delete_then_store_for_an_atom__store_should_propagate_all_three_events() {
		InMemoryAtomStore inMemoryAtomStore = new InMemoryAtomStore();
		Atom atom = mock(Atom.class);
		RadixHash hash = mock(RadixHash.class);
		when(hash.toString()).thenReturn("hash");
		when(atom.getHash()).thenReturn(hash);

		AtomObservation storeObservation = mock(AtomObservation.class);
		when(storeObservation.getAtom()).thenReturn(atom);
		when(storeObservation.isStore()).thenReturn(true);
		when(storeObservation.isHead()).thenReturn(false);
		when(storeObservation.getType()).thenReturn(Type.STORE);
		when(storeObservation.getUpdateType()).thenReturn(AtomObservationUpdateType.of(Type.STORE, false));

		AtomObservation deleteObservation = mock(AtomObservation.class);
		when(deleteObservation.getAtom()).thenReturn(atom);
		when(deleteObservation.isStore()).thenReturn(false);
		when(deleteObservation.isHead()).thenReturn(false);
		when(deleteObservation.getType()).thenReturn(Type.DELETE);
		when(deleteObservation.getUpdateType()).thenReturn(AtomObservationUpdateType.of(Type.DELETE, false));

		RadixAddress address = mock(RadixAddress.class);
		inMemoryAtomStore.store(address, storeObservation);
		inMemoryAtomStore.store(address, deleteObservation);
		inMemoryAtomStore.store(address, storeObservation);

		TestObserver<AtomObservation> testObserver = TestObserver.create();
		inMemoryAtomStore.getAtoms(address).subscribe(testObserver);
		testObserver.assertValues(storeObservation, deleteObservation, storeObservation);
	}
}