package com.radixdlt.client.core.ledger;

import com.radixdlt.client.core.atoms.Atom;
import com.radixdlt.client.core.atoms.RadixHash;
import org.junit.Test;

import com.radixdlt.client.core.atoms.AtomObservation;
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

		TestObserver<AtomObservation> testObserver = TestObserver.create();
		RadixAddress address = mock(RadixAddress.class);

		inMemoryAtomStore.store(address, atomObservation);
		inMemoryAtomStore.getAtoms(address).subscribe(testObserver);

		testObserver.assertValue(atomObservation);
	}
}