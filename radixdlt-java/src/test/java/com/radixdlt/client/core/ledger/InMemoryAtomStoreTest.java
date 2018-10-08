package com.radixdlt.client.core.ledger;

import static org.mockito.Mockito.mock;

import com.radixdlt.client.core.address.RadixAddress;
import com.radixdlt.client.core.atoms.Atom;
import io.reactivex.observers.TestObserver;
import org.junit.Test;

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