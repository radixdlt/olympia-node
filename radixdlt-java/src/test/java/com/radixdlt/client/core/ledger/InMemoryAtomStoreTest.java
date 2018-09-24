package com.radixdlt.client.core.ledger;

import static org.mockito.Mockito.mock;

import com.radixdlt.client.core.address.EUID;
import com.radixdlt.client.core.atoms.Atom;
import io.reactivex.observers.TestObserver;
import java.math.BigInteger;
import org.junit.Test;

public class InMemoryAtomStoreTest {

	@Test
	public void subscribeBeforeStoreAtomTest() {
		InMemoryAtomStore inMemoryAtomStore = new InMemoryAtomStore();
		Atom atom = mock(Atom.class);

		TestObserver<Atom> testObserver = TestObserver.create();
		inMemoryAtomStore.getAtoms(new EUID(BigInteger.ONE)).subscribe(testObserver);
		inMemoryAtomStore.store(new EUID(BigInteger.ONE), atom);

		testObserver.assertValue(atom);
	}

	@Test
	public void subscribeAfterStoreAtomTest() {
		InMemoryAtomStore inMemoryAtomStore = new InMemoryAtomStore();
		Atom atom = mock(Atom.class);

		TestObserver<Atom> testObserver = TestObserver.create();
		inMemoryAtomStore.store(new EUID(BigInteger.ONE), atom);
		inMemoryAtomStore.getAtoms(new EUID(BigInteger.ONE)).subscribe(testObserver);

		testObserver.assertValue(atom);
	}
}