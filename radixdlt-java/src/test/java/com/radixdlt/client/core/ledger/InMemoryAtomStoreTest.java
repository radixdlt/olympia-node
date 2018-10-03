package com.radixdlt.client.core.ledger;

import static org.mockito.Mockito.mock;

import com.radixdlt.client.core.address.EUID;
import com.radixdlt.client.core.atoms.AtomObservation;
import io.reactivex.observers.TestObserver;
import java.math.BigInteger;
import org.junit.Test;

public class InMemoryAtomStoreTest {

	@Test
	public void subscribeBeforeStoreAtomTest() {
		InMemoryAtomStore inMemoryAtomStore = new InMemoryAtomStore();
		AtomObservation atomObservation = mock(AtomObservation.class);

		TestObserver<AtomObservation> testObserver = TestObserver.create();
		inMemoryAtomStore.getAtoms(new EUID(BigInteger.ONE)).subscribe(testObserver);
		inMemoryAtomStore.store(new EUID(BigInteger.ONE), atomObservation);

		testObserver.assertValue(atomObservation);
	}

	@Test
	public void subscribeAfterStoreAtomTest() {
		InMemoryAtomStore inMemoryAtomStore = new InMemoryAtomStore();
		AtomObservation atomObservation = mock(AtomObservation.class);

		TestObserver<AtomObservation> testObserver = TestObserver.create();
		inMemoryAtomStore.store(new EUID(BigInteger.ONE), atomObservation);
		inMemoryAtomStore.getAtoms(new EUID(BigInteger.ONE)).subscribe(testObserver);

		testObserver.assertValue(atomObservation);
	}
}