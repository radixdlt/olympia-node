package com.radixdlt.client.core.ledger;

import org.junit.Test;

import com.radixdlt.client.atommodel.accounts.RadixAddress;
import com.radixdlt.client.core.atoms.Atom;

import static org.mockito.Mockito.mock;

import io.reactivex.observers.TestObserver;

public class InMemoryAtomStoreTest {

	@Test
	public void subscribeBeforeStoreAtomTest() {
		InMemoryAtomStore inMemoryAtomStore = new InMemoryAtomStore();
		Atom atom = mock(Atom.class);
		RadixAddress address = mock(RadixAddress.class);

		TestObserver<Atom> testObserver = TestObserver.create();
		inMemoryAtomStore.getAtoms(address).subscribe(testObserver);
		inMemoryAtomStore.store(address, atom);

		testObserver.assertValue(atom);
	}

	@Test
	public void subscribeAfterStoreAtomTest() {
		InMemoryAtomStore inMemoryAtomStore = new InMemoryAtomStore();
		Atom atom = mock(Atom.class);
		RadixAddress address = mock(RadixAddress.class);

		TestObserver<Atom> testObserver = TestObserver.create();
		inMemoryAtomStore.store(address, atom);
		inMemoryAtomStore.getAtoms(address).subscribe(testObserver);

		testObserver.assertValue(atom);
	}
}