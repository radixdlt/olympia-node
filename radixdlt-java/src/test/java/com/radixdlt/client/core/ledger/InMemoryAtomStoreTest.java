package com.radixdlt.client.core.ledger;

import static org.mockito.Mockito.mock;

import com.radixdlt.client.core.address.EUID;
import com.radixdlt.client.core.atoms.Atom;
import io.reactivex.observers.TestObserver;
import org.junit.Test;

public class InMemoryAtomStoreTest {

	@Test
	public void subscribeBeforeStoreAtomTest() {
		InMemoryAtomStore inMemoryAtomStore = new InMemoryAtomStore();
		Atom atom = mock(Atom.class);

		TestObserver<Atom> testObserver = TestObserver.create();
		inMemoryAtomStore.getAtoms(new EUID(1)).subscribe(testObserver);
		inMemoryAtomStore.store(new EUID(1), atom);

		testObserver.assertValue(atom);
	}

	@Test
	public void subscribeAfterStoreAtomTest() {
		InMemoryAtomStore inMemoryAtomStore = new InMemoryAtomStore();
		Atom atom = mock(Atom.class);

		TestObserver<Atom> testObserver = TestObserver.create();
		inMemoryAtomStore.store(new EUID(1), atom);
		inMemoryAtomStore.getAtoms(new EUID(1)).subscribe(testObserver);

		testObserver.assertValue(atom);
	}
}